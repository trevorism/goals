package com.trevorism.service

import com.trevorism.data.Repository
import com.trevorism.data.model.filtering.FilterConstants
import com.trevorism.data.model.filtering.SimpleFilter
import com.trevorism.model.GoalDelegate
import com.trevorism.model.types.DelegateAccessType
import jakarta.inject.Named
import jakarta.inject.Singleton

import static com.trevorism.service.Validation.require
import static com.trevorism.service.Validation.requireOneOf

@Singleton
class DelegateService {

    private final OwnedRepository<GoalDelegate> delegateRepository
    private final Repository<GoalDelegate> delegateStore

    DelegateService(@Named("delegate") OwnedRepository<GoalDelegate> delegateRepository,
                    @Named("delegateStore") Repository<GoalDelegate> delegateStore) {
        this.delegateRepository = delegateRepository
        this.delegateStore = delegateStore
    }

    List<GoalDelegate> list(String ownerId) {
        delegateRepository.list(ownerId).sort { it.createdDate }
    }

    GoalDelegate grant(String ownerId, GoalDelegate request) {
        String delegateId = request.delegateId?.trim()
        String label = request.label?.trim()
        String access = request.access ?: DelegateAccessType.EDIT
        require(delegateId as boolean, "delegateId is required")
        require(delegateId != ownerId, "You already have access to your own goals")
        require(label as boolean, "label is required")
        requireOneOf(access, DelegateAccessType.ALL, "access")
        GoalDelegate existing = delegateRepository.listWhere(ownerId, "delegateId", delegateId).find()
        if (existing) {
            existing.label = label
            existing.access = access
            return delegateRepository.update(ownerId, existing.id, existing)
        }
        delegateRepository.create(ownerId, new GoalDelegate(delegateId: delegateId, label: label, access: access, createdDate: new Date()))
    }

    GoalDelegate revoke(String ownerId, String id) {
        delegateRepository.delete(ownerId, id)
    }

    List<GoalDelegate> grantedTo(String delegateId) {
        delegateStore.filter(new SimpleFilter("delegateId", FilterConstants.OPERATOR_EQUAL, delegateId)).sort { it.createdDate }
    }
}
