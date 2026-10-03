package com.trevorism.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test

class GoalTreeNodeTest {

    @Test
    void testALeafNodeKeepsItsEmptyListsUnderMicronautsNonEmptyDefault() {
        ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_EMPTY)

        Map json = mapper.readValue(mapper.writeValueAsString(new GoalTreeNode(goal: new Goal(id: "1", title: "leaf"))), Map)

        assert json.children == []
        assert json.metrics == []
        assert json.goal.title == "leaf"
    }
}
