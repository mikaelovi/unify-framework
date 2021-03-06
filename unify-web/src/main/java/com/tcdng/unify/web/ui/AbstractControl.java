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
package com.tcdng.unify.web.ui;

import com.tcdng.unify.core.UnifyException;
import com.tcdng.unify.core.annotation.UplAttribute;
import com.tcdng.unify.core.annotation.UplAttributes;
import com.tcdng.unify.core.constant.TriState;
import com.tcdng.unify.web.DataTransferBlock;
import com.tcdng.unify.web.ui.control.ControlColorMode;
import com.tcdng.unify.web.util.WidgetUtils;

/**
 * Abstract user interface control.
 * 
 * @author Lateef Ojulari
 * @since 1.0
 */
@UplAttributes({ @UplAttribute(name = "focus", type = boolean.class),
        @UplAttribute(name = "sortable", type = boolean.class),
        @UplAttribute(name = "required", type = boolean.class, defaultVal = "false"),
        @UplAttribute(name = "layoutColorMode", type = boolean.class, defaultVal = "false")})
public abstract class AbstractControl extends AbstractWidget implements Control {

    private TriState required;

    private ControlColorMode colorMode;

    public AbstractControl() {
        required = TriState.CONFORMING;
        colorMode = ControlColorMode.NORMAL;
    }

    @Override
    public String getStyleClass() throws UnifyException {
        if (ControlColorMode.NORMAL.equals(colorMode) || isLayoutColorMode()) {
            return super.getStyleClass();
        }

        return super.getStyleClass() + " " + colorMode.styleClass();
    }

    @Override
    public String getId() throws UnifyException {
        int index = getValueIndex();
        if (index >= 0) {
            return WidgetUtils.getDataIndexId(super.getId(), index);
        }

        return super.getId();
    }

    @Override
    public String getBaseId() throws UnifyException {
        return super.getId();
    }

    @Override
    public String getFacadeId() throws UnifyException {
        return getPrefixedId("fac_");
    }

    @Override
    public String getBorderId() throws UnifyException {
        return getId();
    }

    @Override
    public String getNotificationId() throws UnifyException {
        return getPrefixedId("notf_");
    }

    @Override
    public void populate(DataTransferBlock transferBlock) throws UnifyException {
        getValueStore().store(transferBlock.getItemIndex(), getBinding(), transferBlock.getValue());
    }

    @Override
    public boolean setFocus() throws UnifyException {
        return getRequestContextUtil().setFocusOnWidgetId(getId());
    }

    @Override
    public boolean isFocus() throws UnifyException {
        return getUplAttribute(boolean.class, "focus");
    }

    @Override
    public boolean isLayoutColorMode() throws UnifyException {
        return getUplAttribute(boolean.class, "layoutColorMode");
    }

    @Override
    public void setRequired(TriState required) throws UnifyException {
        this.required = required;
    }

    @Override
    public TriState getRequired() throws UnifyException {
        TriState result = getViewDirective().getRequired();
        if (result.isConforming()) {
            result = required;
            if (result.isConforming()) {
                result = TriState.getTriState(getUplAttribute(boolean.class, "required"));
            }
        }

        return result;
    }

    @Override
    public boolean isField() {
        return true;
    }

    @Override
    public void updateState() throws UnifyException {

    }

    @Override
    public void setColorMode(ControlColorMode colorMode) {
        this.colorMode = colorMode;
    }

    @Override
    public ControlColorMode getColorMode() {
        return colorMode;
    }
}
