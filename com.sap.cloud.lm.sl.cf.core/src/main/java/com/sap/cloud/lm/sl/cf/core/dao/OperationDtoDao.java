package com.sap.cloud.lm.sl.cf.core.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.sap.cloud.lm.sl.cf.core.dao.filters.OperationFilter;
import com.sap.cloud.lm.sl.cf.core.dto.persistence.OperationDto;
import com.sap.cloud.lm.sl.cf.core.message.Messages;
import com.sap.cloud.lm.sl.cf.web.api.model.State;
import com.sap.cloud.lm.sl.common.ConflictException;
import com.sap.cloud.lm.sl.common.NotFoundException;
import com.sap.cloud.lm.sl.common.SLException;

@Component
public class OperationDtoDao {

    @Autowired
    @Qualifier("operationEntityManagerFactory")
    EntityManagerFactory emf;

    public void add(OperationDto operation) throws ConflictException {
        new TransactionalExecutor<Void>(createEntityManager()).execute((manager) -> {
            if (existsInternal(manager, operation.getProcessId())) {
                throw new ConflictException(Messages.OPERATION_ALREADY_EXISTS, operation.getProcessId());
            }
            manager.persist(operation);
            return null;
        });
    }

    private EntityManager createEntityManager() {
        return emf.createEntityManager();
    }

    private boolean existsInternal(EntityManager manager, String processId) {
        return manager.find(OperationDto.class, processId) != null;
    }

    public void remove(String processId) throws NotFoundException {
        new TransactionalExecutor<Void>(createEntityManager()).execute((manager) -> {
            OperationDto dto = manager.find(OperationDto.class, processId);
            if (dto == null) {
                throw new NotFoundException(Messages.OPERATION_NOT_FOUND, processId);
            }
            manager.remove(dto);
            return null;
        });
    }

    public OperationDto find(String processId) {
        return new Executor<OperationDto>(createEntityManager()).execute((manager) -> {
            return manager.find(OperationDto.class, processId);
        });
    }

    public OperationDto findRequired(String processId) throws NotFoundException {
        OperationDto dto = find(processId);
        if (dto == null) {
            throw new NotFoundException(Messages.OPERATION_NOT_FOUND, processId);
        }
        return dto;
    }

    public List<OperationDto> find(OperationFilter filter) {
        return new Executor<List<OperationDto>>(createEntityManager()).execute((manager) -> {
            return createQuery(manager, filter).getResultList();
        });
    }

    @SuppressWarnings("unchecked")
    public List<OperationDto> findAll() {
        return new Executor<List<OperationDto>>(createEntityManager()).execute((manager) -> {
            return manager.createNamedQuery("find_all").getResultList();
        });
    }

    @SuppressWarnings("unchecked")
    public List<OperationDto> findAllInSpace(String spaceId) {
        return new Executor<List<OperationDto>>(createEntityManager()).execute((manager) -> {
            return manager.createNamedQuery("find_all_in_space")
                .setParameter(OperationDto.AttributeNames.SPACE_ID, spaceId)
                .getResultList();
        });
    }

    @SuppressWarnings("unchecked")
    public List<OperationDto> findLastOperations(int last, String spaceId) {
        return new Executor<List<OperationDto>>(createEntityManager()).execute((manager) -> {
            return manager.createNamedQuery("find_all_in_space_desc")
                .setParameter(OperationDto.AttributeNames.SPACE_ID, spaceId)
                .setMaxResults(last)
                .getResultList();
        });
    }

    public List<OperationDto> findActiveOperations(String spaceId, List<State> requestedActiveStates) {
        return new Executor<List<OperationDto>>(createEntityManager()).execute((manager) -> {
            return createQuery(spaceId, true, requestedActiveStates, manager).getResultList();
        });
    }

    public List<OperationDto> findFinishedOperations(String spaceId, List<State> requestedFinishedStates) {
        return new Executor<List<OperationDto>>(createEntityManager()).execute((manager) -> {
            return createQuery(spaceId, false, requestedFinishedStates, manager).getResultList();
        });
    }

    public List<OperationDto> findAllInSpaceByStatus(List<State> requestedStates, String spaceId) {
        return new Executor<List<OperationDto>>(createEntityManager()).execute((manager) -> {
            return createQuery(spaceId, null, requestedStates, manager).getResultList();
        });
    }

    public List<OperationDto> findOperationsByStatus(List<State> requestedStates, String spaceId) {
        boolean parametersContainsOnlyFinishedStates = Collections.disjoint(requestedStates, State.getActiveStates());
        boolean parametersContainsOnlyActiveStates = Collections.disjoint(requestedStates, State.getFinishedStates());
        if (parametersContainsOnlyActiveStates) {
            return findActiveOperations(spaceId, requestedStates);
        } else if (parametersContainsOnlyFinishedStates) {
            return findFinishedOperations(spaceId, requestedStates);
        }
        return findAllInSpaceByStatus(requestedStates, spaceId);
    }

    public void merge(OperationDto operation) throws NotFoundException {
        new TransactionalExecutor<Void>(createEntityManager()).execute((manager) -> {
            OperationDto dto = manager.find(OperationDto.class, operation.getProcessId());
            if (dto == null) {
                throw new NotFoundException(Messages.OPERATION_NOT_FOUND, operation.getProcessId());
            }
            manager.merge(operation);
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    public OperationDto findProcessWithLock(String mtaId, String spaceId) throws SLException {
        return new Executor<OperationDto>(createEntityManager()).execute((manager) -> {
            List<OperationDto> processes = manager.createNamedQuery("find_mta_lock")
                .setParameter(OperationDto.AttributeNames.MTA_ID, mtaId)
                .setParameter(OperationDto.AttributeNames.SPACE_ID, spaceId)
                .getResultList();
            if (processes.size() == 0) {
                return null;
            }
            if (processes.size() == 1) {
                return processes.get(0);
            }
            throw new SLException(Messages.MULTIPLE_OPERATIONS_WITH_LOCK_FOUND, mtaId, spaceId);
        });
    }

    private TypedQuery<OperationDto> createQuery(String spaceId, Boolean shouldFinalStateBeNull, List<State> statusList,
        EntityManager manager) {
        CriteriaBuilder criteriaBuilder = manager.getCriteriaBuilder();
        CriteriaQuery<OperationDto> query = criteriaBuilder.createQuery(OperationDto.class);
        Root<OperationDto> root = query.from(OperationDto.class);
        Predicate spaceIdPredicate = null;

        if (spaceId != null) {
            spaceIdPredicate = criteriaBuilder.equal(root.get(OperationDto.AttributeNames.SPACE_ID), spaceId);
        }

        List<Predicate> predicates = new ArrayList<>();

        if (shouldFinalStateBeNull != null) {
            predicates.add(getFinalStateNullPredicate(shouldFinalStateBeNull, root));
        }

        for (State status : statusList) {
            predicates.add(criteriaBuilder.equal(root.get(OperationDto.AttributeNames.FINAL_STATE), status.toString()));
        }

        Predicate finalStatePredicate = criteriaBuilder.or(predicates.toArray(new Predicate[0]));
        Predicate wherePredicate = criteriaBuilder.and(spaceIdPredicate, finalStatePredicate);

        return manager.createQuery(query.select(root).where(wherePredicate));
    }

    private TypedQuery<OperationDto> createQuery(EntityManager manager, OperationFilter operationFilter) {
        CriteriaBuilder criteriaBuilder = manager.getCriteriaBuilder();
        CriteriaQuery<OperationDto> query = criteriaBuilder.createQuery(OperationDto.class);
        Root<OperationDto> root = query.from(OperationDto.class);

        List<Predicate> predicates = new ArrayList<>();

        if (operationFilter.getSpaceId() != null) {
            predicates.add(criteriaBuilder.equal(root.get(OperationDto.AttributeNames.SPACE_ID), operationFilter.getSpaceId()));
        }
        if (operationFilter.getMtaId() != null) {
            predicates.add(criteriaBuilder.equal(root.get(OperationDto.AttributeNames.MTA_ID), operationFilter.getMtaId()));
        }
        if (operationFilter.getUser() != null) {
            predicates.add(criteriaBuilder.equal(root.get(OperationDto.AttributeNames.USER), operationFilter.getUser()));
        }
        if (operationFilter.isInNonFinalState()) {
            predicates.add(root.get(OperationDto.AttributeNames.FINAL_STATE).isNull());
        }
        if (operationFilter.isInFinalState()) {
            predicates.add(root.get(OperationDto.AttributeNames.FINAL_STATE).isNotNull());
        }
        if (operationFilter.getEndTimeUpperBound() != null) {
            predicates.add(criteriaBuilder.lessThan(root.get(OperationDto.AttributeNames.ENDED_AT), operationFilter.getEndTimeUpperBound()));
        }
        if (operationFilter.getEndTimeLowerBound() != null) {
            predicates
                .add(criteriaBuilder.greaterThan(root.get(OperationDto.AttributeNames.ENDED_AT), operationFilter.getEndTimeLowerBound()));
        }

        return manager.createQuery(query.select(root).where(predicates.toArray(new Predicate[0])));
    }

    private Predicate getFinalStateNullPredicate(Boolean shouldBeNull, Root<OperationDto> root) {
        if (shouldBeNull) {
            return root.get(OperationDto.AttributeNames.FINAL_STATE).isNull();
        }
        return root.get(OperationDto.AttributeNames.FINAL_STATE).isNotNull();
    }

}
