package com.trevorism.model

class Goal implements Owned {

    String id
    String ownerId
    String parentId
    String rootId

    String title
    String description
    String status
    Date startDate
    Date endDate
    String definitionOfDone

    String createdBy
    Date createdDate
    Date completedDate

    String treeRootId() {
        rootId ?: id
    }
}
