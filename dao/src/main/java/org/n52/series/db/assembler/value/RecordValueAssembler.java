/*
 * Copyright (C) 2015-2019 52°North Initiative for Geospatial Open Source
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
package org.n52.series.db.assembler.value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.n52.io.response.dataset.Data;
import org.n52.io.response.dataset.DatasetMetadata;
import org.n52.io.response.dataset.DatasetOutput;
import org.n52.io.response.dataset.ReferenceValueOutput;
import org.n52.io.response.dataset.record.RecordValue;
import org.n52.series.db.ValueAssemblerComponent;
import org.n52.series.db.beans.DatasetEntity;
import org.n52.series.db.beans.ProcedureEntity;
import org.n52.series.db.beans.RecordDataEntity;
import org.n52.series.db.old.dao.DbQuery;
import org.n52.series.db.repositories.core.DataRepository;
import org.n52.series.db.repositories.core.DatasetRepository;

@ValueAssemblerComponent(value = "record", datasetEntityType = DatasetEntity.class)
public class RecordValueAssembler extends AbstractValueAssembler<RecordDataEntity, RecordValue, Map<String, Object>> {

    public RecordValueAssembler(DataRepository<RecordDataEntity> dataRepository,
            DatasetRepository datasetRepository) {
        super(dataRepository, datasetRepository);
    }

    @Override
    public RecordValue assembleDataValue(RecordDataEntity data, DatasetEntity dataset, DbQuery query) {
        final RecordValue assembledValue = prepareValue(new RecordValue(), data, query);
        assembledValue.setValue(getDataValue(data, dataset));
        return assembledValue;
    }

    private Map<String, Object> getDataValue(RecordDataEntity observation, DatasetEntity dataset) {
        return !isNoDataValue(observation, dataset) && observation.getValue() != null ? observation.getValue() : null;
    }

    @Override
    protected Data<RecordValue> assembleExpandedDataValues(DatasetEntity dataset, DbQuery query) {
        Data<RecordValue> result = assembleDataValues(dataset, query);
        DatasetMetadata<RecordValue> metadata = result.getMetadata();

        RecordDataEntity previousValue = getClosestValueAfterEnd(dataset, query);
        RecordDataEntity nextValue = getClosestValueAfterEnd(dataset, query);
        metadata.setValueBeforeTimespan(assembleDataValue(previousValue, dataset, query));
        metadata.setValueAfterTimespan(assembleDataValue(nextValue, dataset, query));

        List<DatasetEntity> referenceValues = dataset.getReferenceValues();
        if ((referenceValues != null) && !referenceValues.isEmpty()) {
            metadata.setReferenceValues(assembleReferenceSeries(referenceValues, query));
        }
        return result;
    }

    private Map<String, Data<RecordValue>> assembleReferenceSeries(List<DatasetEntity> referenceValues,
            DbQuery query) {
        Map<String, Data<RecordValue>> referenceSeries = new HashMap<>();
        for (DatasetEntity referenceSeriesEntity : referenceValues) {
            if (referenceSeriesEntity.isPublished()) {
                Data<RecordValue> referenceSeriesData = assembleDataValues(referenceSeriesEntity, query);
                if (haveToExpandReferenceData(referenceSeriesData)) {
                    referenceSeriesData = expandReferenceDataIfNecessary(referenceSeriesEntity, query);
                }
                referenceSeries.put(createReferenceDatasetId(query, referenceSeriesEntity), referenceSeriesData);
            }
        }
        return referenceSeries;
    }

    private boolean haveToExpandReferenceData(Data<RecordValue> referenceSeriesData) {
        List<RecordValue> values = referenceSeriesData.getValues();
        return values.size() <= 1;
    }

    private Data<RecordValue> expandReferenceDataIfNecessary(DatasetEntity dataset, DbQuery query) {
        Data<RecordValue> result = new Data<>();
        List<RecordDataEntity> observations = findAll(dataset, query).collect(Collectors.toList());
        if (!hasValidEntriesWithinRequestedTimespan(observations)) {
            RecordValue lastValue = getLastValue(dataset, query);
            result.addValues(expandToInterval(lastValue.getValue(), dataset, query));
        }

        if (hasSingleValidReferenceValue(observations)) {
            RecordDataEntity entity = observations.get(0);
            result.addValues(expandToInterval(entity.getValue(), dataset, query));
        }
        return result;
    }

    private RecordValue[] expandToInterval(Map<String, Object> value, DatasetEntity series, DbQuery query) {
        RecordDataEntity referenceStart = new RecordDataEntity();
        RecordDataEntity referenceEnd = new RecordDataEntity();
        referenceStart.setSamplingTimeEnd(query.getTimespan().getStart().toDate());
        referenceEnd.setSamplingTimeEnd(query.getTimespan().getEnd().toDate());
        referenceStart.setValue(value);
        referenceEnd.setValue(value);
        return new RecordValue[] { assembleDataValue(referenceStart, series, query),
                assembleDataValue(referenceEnd, series, query), };
    }

    @Override
    public List<ReferenceValueOutput<RecordValue>> getReferenceValues(DatasetEntity datasetEntity, DbQuery query) {
        List<DatasetEntity> referenceValues = datasetEntity.getReferenceValues();
        List<ReferenceValueOutput<RecordValue>> outputs = new ArrayList<>();
        for (DatasetEntity referenceSeriesEntity : referenceValues) {
            ReferenceValueOutput<RecordValue> refenceValueOutput = new ReferenceValueOutput<>();
            ProcedureEntity procedure = referenceSeriesEntity.getProcedure();
            refenceValueOutput.setLabel(procedure.getNameI18n(query.getLocale()));
            refenceValueOutput.setReferenceValueId(createReferenceDatasetId(query, referenceSeriesEntity));

            RecordDataEntity lastValue = (RecordDataEntity) referenceSeriesEntity.getLastObservation();
            refenceValueOutput.setLastValue(assembleDataValue(lastValue, referenceSeriesEntity, query));
            outputs.add(refenceValueOutput);
        }
        return outputs;
    }

    private String createReferenceDatasetId(DbQuery query, DatasetEntity referenceSeriesEntity) {
        DatasetOutput<?> dataset = new DatasetOutput<>();
        Long id = referenceSeriesEntity.getId();
        dataset.setId(id.toString());

        String referenceDatasetId = dataset.getId();
        return referenceDatasetId.toString();
    }

}
