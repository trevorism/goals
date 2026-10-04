package com.trevorism.service

import com.trevorism.controller.RequesterIdentity
import com.trevorism.data.Repository
import com.trevorism.data.model.filtering.FilterBuilder
import com.trevorism.data.model.filtering.FilterConstants
import com.trevorism.data.model.filtering.SimpleFilter
import com.trevorism.model.GoalDelegate
import com.trevorism.model.types.DelegateAccessType
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.security.authentication.Authentication
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Singleton
class OwnerResolver {

    static final String ON_BEHALF_OF = "onBehalfOf"
    static final String DELEGATE_ATTRIBUTE = "goals.delegateId"

    private static final Logger log = LoggerFactory.getLogger(OwnerResolver)
    private static final List<HttpMethod> READ_METHODS = [HttpMethod.GET, HttpMethod.HEAD]

    private final Repository<GoalDelegate> delegateStore

    OwnerResolver(@Named("delegateStore") Repository<GoalDelegate> delegateStore) {
        this.delegateStore = delegateStore
    }

    String ownerFor(Authentication authentication) {
        String requester = RequesterIdentity.of(authentication)
        HttpRequest<?> request = ServerRequestContext.currentRequest().orElse(null)
        String owner = resolve(requester, request?.method, request?.parameters?.get(ON_BEHALF_OF))
        if (owner != requester) {
            request.setAttribute(DELEGATE_ATTRIBUTE, requester)
        }
        return owner
    }

    String resolve(String requester, HttpMethod method, String onBehalfOf) {
        if (!onBehalfOf || onBehalfOf == requester) {
            return requester
        }
        GoalDelegate grant = grantFrom(onBehalfOf, requester)
        if (grant == null) {
            throw forbidden(requester, onBehalfOf, "That owner hasn't given you access to their goals")
        }
        if (method == HttpMethod.DELETE) {
            throw forbidden(requester, onBehalfOf, "Only the owner can delete")
        }
        if (!(method in READ_METHODS) && grant.access != DelegateAccessType.EDIT) {
            throw forbidden(requester, onBehalfOf, "Your access to these goals is read-only")
        }
        return onBehalfOf
    }

    static String currentDelegate() {
        ServerRequestContext.currentRequest()
                .flatMap { it.getAttribute(DELEGATE_ATTRIBUTE, String) }
                .orElse(null)
    }

    private GoalDelegate grantFrom(String ownerId, String delegateId) {
        delegateStore.filter(new FilterBuilder()
                .addFilter(new SimpleFilter(OwnedRepository.OWNER_FIELD, FilterConstants.OPERATOR_EQUAL, ownerId),
                        new SimpleFilter("delegateId", FilterConstants.OPERATOR_EQUAL, delegateId))
                .build())
                .find()
    }

    private static HttpStatusException forbidden(String requester, String owner, String message) {
        log.info("Rejected delegated request from ${requester} for ${owner}: ${message}")
        new HttpStatusException(HttpStatus.FORBIDDEN, message)
    }
}
