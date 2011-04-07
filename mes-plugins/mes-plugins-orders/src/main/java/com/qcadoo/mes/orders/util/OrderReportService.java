package com.qcadoo.mes.orders.util;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.qcadoo.localization.api.TranslationService;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.security.api.SecurityService;
import com.qcadoo.view.api.ComponentState;
import com.qcadoo.view.api.ComponentState.MessageType;
import com.qcadoo.view.components.grid.GridComponentState;

@Service
public class OrderReportService {

    @Autowired
    private DataDefinitionService dataDefinitionService;

    @Autowired
    private SecurityService securityService;

    @Autowired
    private TranslationService translationService;

    public Entity printWorkPlanForOrder(final ComponentState state) {

        OrderValidator orderValidator = new OrderValidator() {

            @Override
            public String validateOrder(final Entity order) {
                if (order.getField("technology") == null) {
                    return order.getField("number")
                            + ": "
                            + translationService.translate("orders.validate.global.error.orderMustHaveTechnology",
                                    state.getLocale());
                } else if (order.getBelongsToField("technology").getTreeField("operationComponents").isEmpty()) {
                    return order.getField("number")
                            + ": "
                            + translationService.translate("orders.validate.global.error.orderTechnologyMustHaveOperation",
                                    state.getLocale());
                }
                return null;
            }
        };

        return printForOrder(state,"workPlans", "workPlan", "workPlanComponent", null, orderValidator);
    }

    public Entity printMaterialReqForOrder(final ComponentState state) {

        Map<String, Object> entityFieldsMap = new HashMap<String, Object>();
        entityFieldsMap.put("onlyComponents", false);

        OrderValidator orderValidator = new OrderValidator() {

            @Override
            public String validateOrder(final Entity order) {
                if (order.getField("technology") == null) {
                    return order.getField("number")
                            + ": "
                            + translationService.translate("orders.validate.global.error.orderMustHaveTechnology",
                                    state.getLocale());
                }
                return null;
            }
        };

        return printForOrder(state, "materialRequirements", "materialRequirement", "materialRequirementComponent",
                entityFieldsMap, orderValidator);
    }

    private Entity printForOrder(final ComponentState state, final String plugin, final String entityName,
            final String detailEntityName, final Map<String, Object> entityFieldsMap, final OrderValidator orderValidator) {
        if (!(state instanceof GridComponentState)) {
            throw new IllegalStateException("method avalible only for grid");
        }

        GridComponentState gridState = (GridComponentState) state;
        Set<Entity> ordersEntities = new HashSet<Entity>();
        if (gridState.getSelectedEntitiesId().size() == 0) {
            state.addMessage(translationService.translate("core.message.noRecordSelected", state.getLocale()),
                    MessageType.FAILURE);
            return null;
        }
        List<String> errors = new LinkedList<String>();
        for (Long orderId : gridState.getSelectedEntitiesId()) {
            Entity order = dataDefinitionService.get("orders", "order").get(orderId);
            if (order == null) {
                errors.add(translationService.translate("core.message.entityNotFound", state.getLocale()));
                continue;
            }
            String validateMessage = orderValidator.validateOrder(order);
            if (validateMessage == null) {
                ordersEntities.add(order);
            } else {
                errors.add(validateMessage);
            }
        }
        if (errors.size() != 0) {
            StringBuilder errorMessage = new StringBuilder();
            for (String error : errors) {
                errorMessage.append(" - ");
                errorMessage.append(error);
                errorMessage.append("\n");
            }
            state.addMessage(errorMessage.toString(), MessageType.FAILURE, false);
        } else {
            return createNewOrderPrint(ordersEntities, plugin, entityName, detailEntityName, entityFieldsMap, state.getLocale());
        }
        return null;
    }

    private Entity createNewOrderPrint(final Set<Entity> orders, final String plugin, final String entityName,
            final String detailEntityName, final Map<String, Object> entityFieldsMap, final Locale locale) {

        DataDefinition data = dataDefinitionService.get(plugin, entityName);

        Entity materialReq = data.create();

        materialReq.setField("name", generateOrderPrintName(orders, locale));
        materialReq.setField("generated", true);
        materialReq.setField("worker", securityService.getCurrentUserName());
        materialReq.setField("date", new Date());
        if (entityFieldsMap != null) {
            for (Map.Entry<String, Object> entityFieldsMapEntry : entityFieldsMap.entrySet()) {
                materialReq.setField(entityFieldsMapEntry.getKey(), entityFieldsMapEntry.getValue());
            }
        }

        Entity saved = data.save(materialReq);

        for (Entity order : orders) {
            Entity materialReqComponent = dataDefinitionService.get(plugin, detailEntityName).create();
            materialReqComponent.setField("order", order);
            materialReqComponent.setField(entityName, saved);
            dataDefinitionService.get(plugin, detailEntityName).save(materialReqComponent);
        }

        saved = data.get(saved.getId());

        return saved;
    }

    private String generateOrderPrintName(final Set<Entity> orders, final Locale locale) {
        StringBuilder materialReqName = new StringBuilder();
        materialReqName.append(translationService.translate("materialRequirements.materialReq.forOrder", locale));
        int ordersCounter = 0;
        for (Entity order : orders) {
            if (ordersCounter > 2) {
                materialReqName.deleteCharAt(materialReqName.length() - 1);
                materialReqName.append("... (");
                materialReqName.append(translationService.translate("materialRequirements.materialReq.summary", locale));
                materialReqName.append(" ");
                materialReqName.append(orders.size());
                materialReqName.append(")R");
                break;
            }
            materialReqName.append(" ");
            materialReqName.append(order.getField("number"));
            materialReqName.append(",");
            ordersCounter++;
        }
        materialReqName.deleteCharAt(materialReqName.length() - 1);
        return materialReqName.toString();
    }

    interface OrderValidator {

        String validateOrder(Entity order);
    }

}