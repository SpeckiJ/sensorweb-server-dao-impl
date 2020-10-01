/*
 * Copyright (C) 2015-2020 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of
 * the following licenses, the combination of the program with the linked
 * library is not considered a "derivative work" of the program:
 *
 *     - Apache License, version 2.0
 *     - Apache Software License, version 1.0
 *     - GNU Lesser General Public License, version 3
 *     - Mozilla Public License, versions 1.0, 1.1 and 2.0
 *     - Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed
 * under the aforementioned licenses, is permitted by the copyright holders
 * if the distribution is compliant with both the GNU General Public License
 * version 2 and the aforementioned licenses.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 */
package org.n52.series.db.assembler.core;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.n52.io.handler.DatasetFactoryException;
import org.n52.io.request.IoParameters;
import org.n52.io.response.dataset.AbstractValue;
import org.n52.io.response.dataset.DatasetOutput;
import org.n52.io.response.dataset.DatasetParameters;
import org.n52.io.response.dataset.ReferenceValueOutput;
import org.n52.sensorweb.server.db.DatasetTypesMetadata;
import org.n52.sensorweb.server.db.old.dao.DbQuery;
import org.n52.sensorweb.server.db.old.dao.DbQueryFactory;
import org.n52.sensorweb.server.db.query.DatasetQuerySpecifications;
import org.n52.sensorweb.server.db.repositories.core.DatasetRepository;
import org.n52.series.db.DataRepositoryTypeFactory;
import org.n52.series.db.ValueAssembler;
import org.n52.series.db.assembler.ParameterDatasetOutputAssembler;
import org.n52.series.db.assembler.ParameterOutputAssembler;
import org.n52.series.db.assembler.mapper.DatasetOutputMapper;
import org.n52.series.db.assembler.mapper.ParameterOutputSearchResultMapper;
import org.n52.series.db.beans.DatasetEntity;
import org.n52.series.spi.search.DatasetSearchResult;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class DatasetAssembler<V extends AbstractValue<?>>
        extends ParameterOutputAssembler<DatasetEntity, DatasetOutput<V>, DatasetSearchResult>
        implements ParameterDatasetOutputAssembler {

    @PersistenceContext
    private EntityManager entityManager;

    private final DataRepositoryTypeFactory dataRepositoryFactory;

    private DbQueryFactory dbQueryFactory;

    public DatasetAssembler(DatasetRepository parameterRepository, DatasetRepository datasetRepository,
            DataRepositoryTypeFactory dataRepositoryFactory, DbQueryFactory dbQueryFactory) {
        super(parameterRepository, datasetRepository);
        this.dataRepositoryFactory = dataRepositoryFactory;
        this.dbQueryFactory = dbQueryFactory;
    }

    @Override
    protected DatasetOutput<V> prepareEmptyOutput() {
        return new DatasetOutput<>();
    }

    @Override
    protected DatasetSearchResult prepareEmptySearchResult() {
        return new DatasetSearchResult();
    }

    @Override
    protected Specification<DatasetEntity> createPublicPredicate(String id, DbQuery query) {
        DatasetQuerySpecifications dsFilterSpec = DatasetQuerySpecifications.of(query, entityManager);
        return dsFilterSpec.matchFilters().and(dsFilterSpec.matchId(id));
    }

    @Override
    protected Specification<DatasetEntity> createFilterPredicate(DbQuery query) {
        DatasetQuerySpecifications dsFilterSpec = DatasetQuerySpecifications.of(query, entityManager);
        return dsFilterSpec.matchFilters();
    }

    @Override
    protected DatasetOutput<V> createExpanded(DatasetEntity entity, DbQuery query) {
        IoParameters params = query.getParameters();
        DatasetOutput<V> result = (DatasetOutput<V>) getDataset(entity, query);

        entity.setService(getServiceEntity(entity));
        DatasetParameters datasetParams = createDatasetParameters(entity, query.withoutFieldsFilter());

        ValueAssembler<?, V, ?> assembler;
        try {
            assembler = (ValueAssembler<?, V, ?>) dataRepositoryFactory.create(entity.getObservationType().name(),
                    entity.getValueType().name(), DatasetEntity.class);
            V firstValue = assembler.getFirstValue(entity, query);
            V lastValue = assembler.getLastValue(entity, query);

            List<ReferenceValueOutput<V>> refValues = assembler.getReferenceValues(entity, query);
            lastValue = isReferenceSeries(entity) && isCongruentValues(firstValue, lastValue)
                    // ensure we have a valid interval
                    ? firstValue
                    : lastValue;

            result.setValue(DatasetOutput.REFERENCE_VALUES, refValues, params, result::setReferenceValues);
            result.setValue(DatasetOutput.DATASET_PARAMETERS, datasetParams, params, result::setDatasetParameters);
            result.setValue(DatasetOutput.FIRST_VALUE, firstValue, params, result::setFirstValue);
            result.setValue(DatasetOutput.LAST_VALUE, lastValue, params, result::setLastValue);
        } catch (DatasetFactoryException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public DatasetEntity getOrInsertInstance(DatasetEntity dataset) {
        IoParameters parameters = IoParameters.createDefaults();
        DatasetQuerySpecifications dsQS = DatasetQuerySpecifications.of(dbQueryFactory.createFrom(parameters), null);
        List<Specification<DatasetEntity>> specifications = new LinkedList<>();
        if (dataset.getCategory() != null && dataset.getCategory().getId() != null) {
            specifications.add(dsQS.matchFeatures(dataset.getCategory().getId().toString()));
        }
        if (dataset.getFeature() != null && dataset.getFeature().getId() != null) {
            specifications.add(dsQS.matchFeatures(dataset.getFeature().getId().toString()));
        }
        if (dataset.getProcedure() != null && dataset.getProcedure().getId() != null) {
            specifications.add(dsQS.matchProcedures(dataset.getProcedure().getId().toString()));
        }
        if (dataset.getOffering() != null && dataset.getOffering().getId() != null) {
            specifications.add(dsQS.matchOfferings(dataset.getOffering().getId().toString()));
        }
        if (dataset.getPhenomenon() != null && dataset.getPhenomenon().getId() != null) {
            specifications.add(dsQS.matchPhenomena(dataset.getPhenomenon().getId().toString()));
        }
        if (dataset.getPlatform() != null && dataset.getPlatform().getId() != null) {
            specifications.add(dsQS.matchPlatforms(dataset.getPlatform().getId().toString()));
        }
        if (dataset.getService() != null && dataset.getService().getId() != null) {
            specifications.add(dsQS.matchServices(dataset.getService().getId().toString()));
        }
        Specification<DatasetEntity> specification = null;
        for (Specification<DatasetEntity> spec : specifications) {
            if (specification != null && spec != null) {
                specification = specification.and(spec);
            } else {
                specification = spec;
            }
        }
        Optional<DatasetEntity> instance = getParameterRepository().findOne(specification);
        return !instance.isPresent() ? getParameterRepository().saveAndFlush(dataset)
                : update(instance.get(), dataset);
    }

    private DatasetEntity update(DatasetEntity instance, DatasetEntity dataset) {
        boolean minChanged = false;
        boolean maxChanged = false;
        if (!instance.isSetFirstValueAt() || (instance.isSetFirstValueAt() && dataset.isSetFirstValueAt()
                && instance.getFirstValueAt().after(dataset.getFirstValueAt()))) {
            minChanged = true;
            instance.setFirstValueAt(dataset.getFirstValueAt());
            instance.setFirstObservation(dataset.getFirstObservation());
            instance.setFirstQuantityValue(dataset.getFirstQuantityValue());
        }
        if (!instance.isSetLastValueAt() || (instance.isSetLastValueAt() && dataset.isSetLastValueAt()
                && instance.getLastValueAt().before(dataset.getLastValueAt()))) {
            maxChanged = true;
            instance.setLastValueAt(dataset.getLastValueAt());
            instance.setLastObservation(dataset.getLastObservation());
            instance.setLastQuantityValue(dataset.getLastQuantityValue());
        }
        if (minChanged || maxChanged) {
            return getParameterRepository().saveAndFlush(instance);
        }
        return instance;
    }

    private DatasetParameters createDatasetParameters(DatasetEntity dataset, DbQuery query) {
        DatasetParameters metadata = new DatasetParameters();
        metadata.setService(getService(dataset.getService(), query));
        metadata.setOffering(getOffering(dataset, query));
        metadata.setProcedure(getProcedure(dataset, query));
        metadata.setPhenomenon(getPhenomenon(dataset, query));
        metadata.setCategory(getCategory(dataset, query));
        metadata.setPlatform(getPlatform(dataset, query));
        return metadata;
    }

    private boolean isReferenceSeries(DatasetEntity series) {
        return series.getProcedure().isReference();
    }

    private boolean isCongruentValues(AbstractValue<?> firstValue, AbstractValue<?> lastValue) {
        return ((firstValue == null) && (lastValue == null))
                || ((firstValue != null) && lastValue.getTimestamp().equals(firstValue.getTimestamp()))
                || ((lastValue != null) && firstValue.getTimestamp().equals(lastValue.getTimestamp()));
    }

    public List<DatasetTypesMetadata> getDatasetTypesMetadata(DbQuery dbQuery) {
        return getDatasetRepository().getDatasetTypesMetadata(createFilterPredicate(dbQuery));
    }

    @Override
    protected ParameterOutputSearchResultMapper getMapper(DbQuery query) {
        return new DatasetOutputMapper(query);
    }
}
