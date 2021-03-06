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
package com.tcdng.unify.web;

import java.util.Collection;
import java.util.List;

import com.tcdng.unify.core.UnifyException;
import com.tcdng.unify.core.format.FormatHelper;
import com.tcdng.unify.core.util.DataUtils;
import com.tcdng.unify.core.util.StringUtils;
import com.tcdng.unify.web.ui.Page;
import com.tcdng.unify.web.ui.ResponseWriter;

/**
 * Convenient JSON object page controller response.
 * 
 * @author Lateef Ojulari
 * @version 1.0
 */
public abstract class AbstractJsonPageControllerResponse extends AbstractPageControllerResponse {

    private String handlerName;

    public AbstractJsonPageControllerResponse(String handlerName) {
        this.handlerName = handlerName;
    }

    @Override
    public void generate(ResponseWriter writer, Page page) throws UnifyException {
        writer.write("{\"handler\":\"").write(handlerName).write("\"");
        RequestContextUtil reqUtils = getRequestContextUtil();
        doGenerate(writer, page);

        if (reqUtils.isFocusOnWidget()) {
            writer.write(",\"focusOnWidget\":\"").write(reqUtils.getFocusOnWidgetId()).write("\"");
        }

        List<String> saveList = reqUtils.getOnSaveContentWidgets();
        if (DataUtils.isNotBlank(saveList)) {
            writer.write(",\"pSaveList\":").writeJsonArray(saveList);
        }
        writer.write("}");
    }
    
    protected void appendRegisteredDebounceWidgets(ResponseWriter writer, boolean clear) throws UnifyException {
        Collection<String> widgetIds = getRequestContextUtil().getAndClearRegisteredDebounceWidgetIds();
        if (!DataUtils.isBlank(widgetIds)) {
            writer.write(",\"debounceClear\":").write(clear);
            writer.write(",\"debounceList\":").writeJsonArray(widgetIds);
        }
    }

    protected String getTimestampedResourceName(String resourceName) throws UnifyException {
        return StringUtils.underscore(resourceName) + "_" + getFormatHelper().formatNow(FormatHelper.yyyyMMdd_HHmmss);

    }

    protected abstract void doGenerate(ResponseWriter writer, Page page) throws UnifyException;
}
