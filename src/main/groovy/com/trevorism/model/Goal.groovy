package com.trevorism.model

class Goal implements Owned {

    String id
    String ownerId
    String parentId
    String rootId
    Integer depth

    String title
    String description
    String kind
    String status
    Date startDate
    Date endDate
    String definitionOfDone
    Double weight
    Integer sortOrder
    Automation automation

    Date createdDate
    Date completedDate

    String treeRootId() {
        rootId ?: id
    }
}
