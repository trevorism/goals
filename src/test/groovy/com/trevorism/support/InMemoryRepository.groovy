package com.trevorism.support

import com.trevorism.data.Repository
import com.trevorism.data.exception.DataOperationException
import com.trevorism.data.model.filtering.ComplexFilter
import com.trevorism.data.model.filtering.SimpleFilter
import com.trevorism.data.model.paging.PageRequest
import com.trevorism.data.model.sorting.ComplexSort
import com.trevorism.data.model.sorting.Sort

class InMemoryRepository<T> implements Repository<T> {

    final List<T> store = []
    private int nextId = 1

    @Override
    List<T> all() {
        store
    }

    @Override
    List<T> list() {
        store
    }

    @Override
    T get(String id) {
        T item = store.find { it.id == id }
        if (item == null) {
            throw new DataOperationException("Unable to HTTP GET: ${id}", new RuntimeException("500"))
        }
        return item
    }

    @Override
    T create(T item) {
        item.id = String.valueOf(nextId++)
        store << item
        return item
    }

    @Override
    T update(String id, T item) {
        store.removeIf { it.id == id }
        store << item
        return item
    }

    @Override
    T delete(String id) {
        T item = get(id)
        store.remove(item)
        return item
    }

    @Override
    void ping() {
    }

    @Override
    List<T> filter(ComplexFilter filter) {
        store.findAll { T item -> filter.simpleFilters.every { matches(item, it) } }
    }

    @Override
    List<T> filter(SimpleFilter filter) {
        store.findAll { matches(it, filter) }
    }

    @Override
    List<T> page(PageRequest page) {
        throw new UnsupportedOperationException()
    }

    @Override
    List<T> sort(ComplexSort sort) {
        throw new UnsupportedOperationException()
    }

    @Override
    List<T> sort(Sort sort) {
        throw new UnsupportedOperationException()
    }

    private static boolean matches(Object item, SimpleFilter filter) {
        assert filter.operator == "="
        String actual = item[filter.field]?.toString()
        return actual == filter.value
    }
}
