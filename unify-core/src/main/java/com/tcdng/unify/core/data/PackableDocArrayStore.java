/*
 * Copyright 2018-2020 The Code Department.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.tcdng.unify.core.data;

import com.tcdng.unify.core.UnifyException;
import com.tcdng.unify.core.format.Formatter;

/**
 * Packable document array value store.
 * 
 * @author Lateef Ojulari
 * @since 1.0
 */
public class PackableDocArrayStore extends AbstractArrayValueStore<PackableDoc> {

    public PackableDocArrayStore(PackableDoc[] storage, int dataIndex) {
        super(storage, dataIndex);
    }

    @Override
    protected boolean doSettable(PackableDoc storage, String property) throws UnifyException {
        return storage != null && storage.isField(property);
    }

    @Override
    protected boolean doGettable(PackableDoc storage, String property) throws UnifyException {
        return storage != null && storage.isField(property);
    }

    @Override
    protected Object doRetrieve(PackableDoc storage, String property) throws UnifyException {
        return storage.read(property);
    }

    @Override
    protected void doStore(PackableDoc storage, String property, Object value, Formatter<?> formatter)
            throws UnifyException {
        storage.write(property, value, formatter);
    }

}
