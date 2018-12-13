/*
 * Copyright (C) 2015-2018 52°North Initiative for Geospatial Open Source
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
package org.n52.series.db.da;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.n52.series.db.beans.DatasetEntity;
import org.n52.series.db.beans.FeatureEntity;
import org.n52.series.db.beans.ProcedureEntity;
import org.n52.series.db.beans.QuantityDatasetEntity;

@Ignore
public class SessionAwareRepositoryTest {

//    @Test
//    public void when_mobileInsituString_then_recognizeType() {
//        String platformId = getPlatformId(42L, true, true);
//        Assert.assertThat(PlatformType.extractType(platformId), Matchers.is(PlatformType.MOBILE_INSITU));
//    }
//
//    @Test
//    public void when_mobileRemoteString_then_recognizeType() {
//        String platformId = getPlatformId(42L, true, false);
//        Assert.assertThat(PlatformType.extractType(platformId), Matchers.is(PlatformType.MOBILE_REMOTE));
//    }
//
//    @Test
//    public void when_stationaryInsituString_then_recognizeType() {
//        String platformId = getPlatformId(42L, false, true);
//        Assert.assertThat(PlatformType.extractType(platformId), Matchers.is(PlatformType.STATIONARY_INSITU));
//    }
//
//    @Test
//    public void when_stationaryRemoteString_then_recognizeType() {
//        String platformId = getPlatformId(42L, false, false);
//        Assert.assertThat(PlatformType.extractType(platformId), Matchers.is(PlatformType.STATIONARY_REMOTE));
//    }

    private String getPlatformId(Long id, boolean mobile, boolean insitu) {
        ProcedureEntity procedure = new ProcedureEntity();
        procedure.setId(id);

        FeatureEntity feature = new FeatureEntity();
        feature.setId(id);

        DatasetEntity dataset = new QuantityDatasetEntity();
        dataset.setMobile(mobile);
        dataset.setInsitu(insitu);
        dataset.setProcedure(procedure);
        dataset.setFeature(feature);

        SessionAwareRepository repo = new SessionAwareRepository() {};
//        return repo.getPlatformId(dataset);
        return Long.toString(id);
    }

}