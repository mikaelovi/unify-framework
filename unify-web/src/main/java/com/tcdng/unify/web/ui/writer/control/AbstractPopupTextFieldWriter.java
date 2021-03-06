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
package com.tcdng.unify.web.ui.writer.control;

import com.tcdng.unify.core.UnifyException;
import com.tcdng.unify.web.constant.ExtensionType;
import com.tcdng.unify.web.ui.ResponseWriter;
import com.tcdng.unify.web.ui.Widget;
import com.tcdng.unify.web.ui.control.AbstractPopupTextField;

/**
 * Abstract base class for popup text field writers.
 * 
 * @author Lateef Ojulari
 * @since 1.0
 */
public abstract class AbstractPopupTextFieldWriter extends TextFieldWriter {

    @Override
    protected void doWriteBehavior(ResponseWriter writer, Widget widget) throws UnifyException {
        AbstractPopupTextField popupTextField = (AbstractPopupTextField) widget;
        super.doWriteBehavior(writer, popupTextField);

        ExtensionType extensionType = popupTextField.getExtensionType();
        if (isAppendPopup(popupTextField)) {
            // Append popup JS
            if (popupTextField.isPopupAlways()
                    || (popupTextField.isContainerEditable() && !popupTextField.isContainerDisabled())) {
                String facId = popupTextField.getId();
                if (extensionType.isExtended()) {
                    facId = popupTextField.getFacadeId();
                }

                writeOpenPopupJS(writer, "onenter", facId, popupTextField.getBorderId(), popupTextField.getPopupId(),
                        popupTextField.getDisplayTimeOut(), getOnShowAction(), getOnShowParam(popupTextField),
                        getOnHideAction(), getOnHideParam(popupTextField));

                writeOpenPopupJS(writer, "onclick", popupTextField.getPopupButtonId(), popupTextField.getBorderId(),
                        popupTextField.getPopupId(), popupTextField.getDisplayTimeOut(), getOnShowAction(),
                        getOnShowParam(popupTextField), getOnHideAction(), getOnHideParam(popupTextField));
            }

            // Append type specific JS
            appendPopupBehaviour(writer, popupTextField);
        }

        if (extensionType.isExtended()) {
            writer.write("ux.textPopupFitFacade({");
            writer.write("\"pBrdId\":\"").write(popupTextField.getBorderId()).write('"');
            writer.write(",\"pBtnId\":\"").write(popupTextField.getPopupButtonId()).write('"');
            if (extensionType.isFacade()) {
                writer.write(",\"pFacId\":\"").write(popupTextField.getFacadeId()).write('"');
            } else {
                writer.write(",\"pFacId\":\"").write(popupTextField.getId()).write('"');
            }
            writer.write("});");
        }
    }

    @Override
    protected void writeTrailingAddOn(ResponseWriter writer, Widget widget) throws UnifyException {
        AbstractPopupTextField popupTextField = (AbstractPopupTextField) widget;
        writer.write("<button");
        writeTagId(writer, popupTextField.getPopupButtonId());
        writeTagStyleClass(writer, "tpbutton");
        if (!isAppendPopup(popupTextField)) {
            writer.write(" disabled");
        }

        writer.write("><img src=\"");
        writer.writeFileImageContextURL(popupTextField.getButtonImageSrc());
        writer.write("\"/></button>");
    }

    @Override
    protected void writeBaseAddOn(ResponseWriter writer, Widget widget) throws UnifyException {
        AbstractPopupTextField popupTextField = (AbstractPopupTextField) widget;
        if (isAppendPopup(popupTextField)) {
            writer.write("<div");
            writeTagId(writer, popupTextField.getPopupId());
            writeTagStyleClass(writer, "ui-text-popup-win");
            writer.write(">");
            appendPopupContent(writer, popupTextField);
            writer.write("</div>");
        }
    }

    protected boolean isAppendPopup(AbstractPopupTextField popupTextField) throws UnifyException {
        if (popupTextField.isPopupOnEditableOnly()) {
            return popupTextField.isContainerEditable() && !popupTextField.isContainerDisabled();
        }
        return true;
    }
    
    protected abstract void appendPopupContent(ResponseWriter writer, AbstractPopupTextField popupTextField)
            throws UnifyException;

    protected abstract void appendPopupBehaviour(ResponseWriter writer, AbstractPopupTextField popupTextField)
            throws UnifyException;

    /**
     * Returns the name of action to fire on show of popup.
     * 
     * @throws UnifyException
     *             if an error occurs
     */
    protected abstract String getOnShowAction() throws UnifyException;

    /**
     * Generates a JSON object for show action parameter
     * 
     * @param popupTextField
     *            the popup text field
     * @return the generated object
     * @throws UnifyException
     *             if an error occurs
     */
    protected abstract String getOnShowParam(AbstractPopupTextField popupTextField) throws UnifyException;

    /**
     * Returns the name of action to fire on hide of popup.
     * 
     * @throws UnifyException
     *             if an error occurs
     */
    protected abstract String getOnHideAction() throws UnifyException;

    /**
     * Generates a JSON object for hide action parameter
     * 
     * @param popupTextField
     *            the popup text field
     * @return the generated object
     * @throws UnifyException
     *             if an error occurs
     */
    protected abstract String getOnHideParam(AbstractPopupTextField popupTextField) throws UnifyException;

}
