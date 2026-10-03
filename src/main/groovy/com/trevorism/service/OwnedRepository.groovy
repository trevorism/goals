package com.trevorism.service

import com.trevorism.data.Repository
import com.trevorism.data.exception.DataOperationException
import com.trevorism.data.model.filtering.FilterBuilder
import com.trevorism.data.model.filtering.FilterConstants
import com.trevorism.data.model.filtering.SimpleFilter
import com.trevorism.model.Owned
import io.micronaut.http.HttpStatus
import io.micronaut.http.exceptions.HttpStatusException

class OwnedRepository<T extends Owned> {

    static final String OWNER_FIELD = "ownerId"

    private final Repository<T> repository
    private final String entityName

    OwnedRepository(Repository<T> repository, String entityName) {
        this.repository = repository
        this.entityName = entityName
    }

    List<T> list(String ownerId) {
        repository.filter(ownerFilter(ownerId))
    }

    List<T> listWhere(String ownerId, String field, String value) {
        repository.filter(new FilterBuilder()
                .addFilter(ownerFilter(ownerId), new SimpleFilter(field, FilterConstants.OPERATOR_EQUAL, value))
                .build())
    }

    T get(String ownerId, String id) {
        T item = findById(id)
        if (item == null || !ownerId || item.ownerId != ownerId) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "${entityName} not found")
        }
        return item
    }

    T create(String ownerId, T item) {
        requireOwner(ownerId)
        item.id = null
        item.ownerId = ownerId
        repository.create(item)
    }

    T update(String ownerId, String id, T item) {
        get(ownerId, id)
        item.id = id
        item.ownerId = ownerId
        repository.update(id, item)
    }

    T delete(String ownerId, String id) {
        get(ownerId, id)
        repository.delete(id)
    }

    private T findById(String id) {
        if (!id) {
            return null
        }
        try {
            return repository.get(id)
        } catch (DataOperationException ignored) {
            return null
        }
    }

    private static SimpleFilter ownerFilter(String ownerId) {
        requireOwner(ownerId)
        new SimpleFilter(OWNER_FIELD, FilterConstants.OPERATOR_EQUAL, ownerId)
    }

    private static void requireOwner(String ownerId) {
        if (!ownerId) {
            throw new HttpStatusException(HttpStatus.FORBIDDEN, "Request has no identity")
        }
    }
}
