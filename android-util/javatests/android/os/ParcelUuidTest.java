/*
 * Copyright (c) 2016, Stein Eldar johnsen
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package android.os;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

@RunWith(BlockJUnit4ClassRunner.class)
public class ParcelUuidTest {
    @Test
    public void testConstructor() {
        UUID uuid = UUID.randomUUID();

        ParcelUuid parcelUuid = new ParcelUuid(uuid);

        assertSame(uuid, parcelUuid.getUuid());
    }

    @Test
    public void testParcelable() {
        Parcel parcel = Parcel.obtain();

        ParcelUuid parcelUuid = new ParcelUuid(UUID.randomUUID());

        parcelUuid.writeToParcel(parcel, 0);

        ParcelUuid other = parcel.readTypedObject(ParcelUuid.CREATOR);

        assertEquals(parcelUuid, other);
    }
}