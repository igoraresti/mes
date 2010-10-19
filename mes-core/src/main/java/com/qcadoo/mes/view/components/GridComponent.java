package com.qcadoo.mes.view.components;

import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.qcadoo.mes.api.Entity;
import com.qcadoo.mes.api.TranslationService;
import com.qcadoo.mes.internal.DefaultEntity;
import com.qcadoo.mes.model.DataDefinition;
import com.qcadoo.mes.model.FieldDefinition;
import com.qcadoo.mes.model.search.Order;
import com.qcadoo.mes.model.search.Restrictions;
import com.qcadoo.mes.model.search.SearchCriteriaBuilder;
import com.qcadoo.mes.model.search.SearchResult;
import com.qcadoo.mes.model.types.HasManyType;
import com.qcadoo.mes.view.AbstractComponent;
import com.qcadoo.mes.view.ComponentOption;
import com.qcadoo.mes.view.ContainerComponent;
import com.qcadoo.mes.view.SelectableComponent;
import com.qcadoo.mes.view.ViewValue;
import com.qcadoo.mes.view.components.grid.ColumnAggregationMode;
import com.qcadoo.mes.view.components.grid.ColumnDefinition;
import com.qcadoo.mes.view.components.grid.ListData;

/**
 * Grid defines structure used for listing entities. It contains the list of field that can be used for restrictions and the list
 * of columns. It also have default order and default restrictions.
 * 
 * Searchable fields must have searchable type - FieldType#isSearchable().
 * 
 * @apiviz.owns com.qcadoo.mes.core.data.definition.FieldDefinition
 * @apiviz.owns com.qcadoo.mes.core.data.definition.ColumnDefinition
 * @apiviz.uses com.qcadoo.mes.core.data.search.Order
 * @apiviz.uses com.qcadoo.mes.core.data.search.Restriction
 */
public final class GridComponent extends AbstractComponent<ListData> implements SelectableComponent {

    private final Set<FieldDefinition> searchableFields = new HashSet<FieldDefinition>();

    private Set<String> orderableColumns = new HashSet<String>();

    private final Map<String, ColumnDefinition> columns = new LinkedHashMap<String, ColumnDefinition>();

    private String correspondingView;

    private boolean header = true;

    private boolean multiselect = false;

    private boolean paginable = false;

    private boolean deletable = false;

    private boolean creatable = false;

    public GridComponent(final String name, final ContainerComponent<?> parentContainer, final String fieldPath,
            final String sourceFieldPath, final TranslationService translationService) {
        super(name, parentContainer, fieldPath, sourceFieldPath, translationService);
    }

    @Override
    public String getType() {
        return "grid";
    }

    public Collection<ColumnDefinition> getColumns() {
        return columns.values();
    }

    @Override
    public void initializeComponent() {
        for (ComponentOption option : getRawOptions()) {
            if ("header".equals(option.getType())) {
                header = Boolean.parseBoolean(option.getValue());
            } else if ("correspondingView".equals(option.getType())) {
                correspondingView = option.getValue();
            } else if ("paginable".equals(option.getType())) {
                paginable = Boolean.parseBoolean(option.getValue());
            } else if ("multiselect".equals(option.getType())) {
                multiselect = Boolean.parseBoolean(option.getValue());
            } else if ("creatable".equals(option.getType())) {
                creatable = Boolean.parseBoolean(option.getValue());
            } else if ("deletable".equals(option.getType())) {
                deletable = Boolean.parseBoolean(option.getValue());
            } else if ("height".equals(option.getType())) {
                addOption("height", Integer.parseInt(option.getValue()));
            } else if ("width".equals(option.getType())) {
                addOption("width", Integer.parseInt(option.getValue()));
            } else if ("fullScreen".equals(option.getType())) {
                addOption("fullScreen", Boolean.parseBoolean(option.getValue()));
            } else if ("searchable".equals(option.getType())) {
                for (FieldDefinition field : getFields(option.getValue())) {
                    searchableFields.add(field);
                }
            } else if ("orderable".equals(option.getType())) {
                orderableColumns = getColumnNames(option.getValue());
            } else if ("column".equals(option.getType())) {
                ColumnDefinition columnDefinition = new ColumnDefinition(option.getAtrributeValue("name"));
                for (FieldDefinition field : getFields(option.getAtrributeValue("fields"))) {
                    columnDefinition.addField(field);
                }
                columnDefinition.setExpression(option.getAtrributeValue("expression"));
                String width = option.getAtrributeValue("width");
                if (width != null) {
                    columnDefinition.setWidth(Integer.valueOf(width));
                }
                if ("sum".equals(option.getAtrributeValue("aggregation"))) {
                    columnDefinition.setAggregationMode(ColumnAggregationMode.SUM);
                } else {
                    columnDefinition.setAggregationMode(ColumnAggregationMode.NONE);
                }
                if (option.getAtrributeValue("link") != null) {
                    columnDefinition.setLink(Boolean.parseBoolean(option.getAtrributeValue("link")));
                }
                if (option.getAtrributeValue("hidden") != null) {
                    columnDefinition.setHidden(Boolean.parseBoolean(option.getAtrributeValue("hidden")));
                }
                columns.put(columnDefinition.getName(), columnDefinition);
            }
        }

        addOption("multiselect", multiselect);
        addOption("correspondingViewName", correspondingView);
        addOption("paginable", paginable);
        addOption("header", header);
        addOption("columns", getColumnsForOptions());
        addOption("fields", getFieldsForOptions(getDataDefinition().getFields()));
        addOption("sortable", !orderableColumns.isEmpty());
        addOption("sortColumns", orderableColumns);
        addOption("filter", !searchableFields.isEmpty());
        addOption("canDelete", deletable);
        addOption("canNew", creatable);
        addOption("prioritizable", getDataDefinition().isPrioritizable());

    }

    private Set<String> getColumnNames(final String columns) {
        Set<String> set = new HashSet<String>();
        for (String column : columns.split("\\s*,\\s*")) {
            set.add(column);
        }
        return set;
    }

    private Set<FieldDefinition> getFields(final String fields) {
        Set<FieldDefinition> set = new HashSet<FieldDefinition>();
        for (String field : fields.split("\\s*,\\s*")) {
            set.add(getDataDefinition().getField(field));
        }
        return set;
    }

    @Override
    public ViewValue<ListData> castComponentValue(final Map<String, Entity> selectedEntities, final JSONObject viewObject)
            throws JSONException {
        ListData listData = new ListData();

        JSONObject value = viewObject.getJSONObject("value");

        if (value != null) {

            if (!value.isNull("paging")) {
                if (!value.getJSONObject("paging").isNull("first")) {
                    listData.setFirstResult(value.getJSONObject("paging").getInt("first"));
                }
                if (!value.getJSONObject("paging").isNull("max")) {
                    listData.setMaxResults(value.getJSONObject("paging").getInt("max"));
                }
            }

            if (!value.isNull("sort")) {
                if (!value.getJSONObject("sort").isNull("column")) {
                    listData.setOrderColumn(value.getJSONObject("sort").getString("column"));
                }
                if (!value.getJSONObject("sort").isNull("order") && "asc".equals(value.getJSONObject("sort").getString("order"))) {
                    listData.setOrderAsc(true);
                } else {
                    listData.setOrderAsc(false);
                }
            }

            if (!value.isNull("filters")) {
                JSONArray filters = value.getJSONArray("filters");
                for (int i = 0; i < filters.length(); i++) {
                    listData.addFilter(((JSONObject) filters.get(i)).getString("column"),
                            ((JSONObject) filters.get(i)).getString("value"));
                }
            }

            if (!value.isNull("selectedEntityId")) {
                String selectedEntityId = value.getString("selectedEntityId");

                if (selectedEntityId != null && !"null".equals(selectedEntityId)) {
                    Entity selectedEntity = getDataDefinition().get(Long.parseLong(selectedEntityId));
                    selectedEntities.put(getPath(), selectedEntity);
                    listData.setSelectedEntityId(Long.parseLong(selectedEntityId));
                }
            }
        }

        return new ViewValue<ListData>(listData);
    }

    @Override
    public ViewValue<ListData> getComponentValue(final Entity entity, final Entity parentEntity,
            final Map<String, Entity> selectedEntities, final ViewValue<ListData> viewValue, final Set<String> pathsToUpdate,
            final Locale locale) {

        String joinFieldName = null;
        Long belongsToEntityId = null;
        SearchCriteriaBuilder searchCriteriaBuilder = null;

        System.out.println(" --> " + entity + ", " + getSourceFieldPath() + ", " + getFieldPath());

        if (getSourceFieldPath() != null || getFieldPath() != null) {
            if (entity == null) {
                return new ViewValue<ListData>(new ListData(0, Collections.<Entity> emptyList()));
            }
            DataDefinition gridDataDefinition = getParentContainer().getDataDefinition();
            if (getSourceComponent() != null) {
                gridDataDefinition = getSourceComponent().getDataDefinition();
            }
            HasManyType hasManyType = null;
            if (getFieldPath() != null) {
                hasManyType = getHasManyType(gridDataDefinition, getFieldPath());
            } else {
                hasManyType = getHasManyType(gridDataDefinition, getSourceFieldPath());
            }
            checkState(hasManyType.getDataDefinition().getName().equals(getDataDefinition().getName()),
                    "Grid and hasMany relation have different data definitions");
            searchCriteriaBuilder = getDataDefinition().find();
            searchCriteriaBuilder = searchCriteriaBuilder.restrictedWith(Restrictions.belongsTo(
                    getDataDefinition().getField(hasManyType.getJoinFieldName()), entity.getId()));
            joinFieldName = hasManyType.getJoinFieldName();
            belongsToEntityId = entity.getId();
        } else {
            searchCriteriaBuilder = getDataDefinition().find();
        }

        if (viewValue != null) {
            addRestrictionsOrderAndPaging(searchCriteriaBuilder, viewValue.getValue());

        }

        ListData listData = generateListData(searchCriteriaBuilder.list(), joinFieldName, belongsToEntityId);

        if (viewValue != null) {
            copyRestrictionsOrderPagingAndSelectedEntityId(listData, viewValue.getValue());
        }

        return new ViewValue<ListData>(listData);
    }

    private void addRestrictionsOrderAndPaging(final SearchCriteriaBuilder searchCriteriaBuilder, final ListData listData) {
        if (listData.getFirstResult() != null) {
            searchCriteriaBuilder.withFirstResult(listData.getFirstResult());
        }
        if (listData.getMaxResults() != null) {
            searchCriteriaBuilder.withMaxResults(listData.getMaxResults());
        }
        if (listData.getOrderColumn() != null) {
            FieldDefinition field = getFieldByColumnName(listData.getOrderColumn());

            if (field != null && field.getType().isOrderable()) {
                Order order = null;

                if (listData.isOrderAsc()) {
                    order = Order.asc(field.getName());
                } else {
                    order = Order.desc(field.getName());
                }

                searchCriteriaBuilder.orderBy(order);
            }
        }
        for (Map<String, String> filter : listData.getFilters()) {
            FieldDefinition field = getFieldByColumnName(filter.get("column"));
            String value = filter.get("value");

            if (field != null && field.getType().isSearchable()) {
                if (field.getType().getType().equals(String.class)) {
                    value += "*";
                }
                searchCriteriaBuilder.restrictedWith(Restrictions.eq(field, value));
            }
        }

    }

    private FieldDefinition getFieldByColumnName(final String column) {
        List<FieldDefinition> fields = columns.get(column).getFields();

        if (fields.size() != 1) {
            return null;
        } else {
            return fields.get(0);
        }
    }

    private void copyRestrictionsOrderPagingAndSelectedEntityId(final ListData listData, final ListData oldListData) {
        listData.setFirstResult(oldListData.getFirstResult());
        listData.setMaxResults(oldListData.getMaxResults());
        listData.setOrderColumn(oldListData.getOrderColumn());
        listData.setOrderAsc(oldListData.isOrderAsc());
        listData.setSelectedEntityId(oldListData.getSelectedEntityId());
        for (Map<String, String> filter : oldListData.getFilters()) {
            listData.addFilter(filter.get("column"), filter.get("value"));
        }
    }

    @Override
    public void addComponentTranslations(final Map<String, String> translationsMap, final Locale locale) {
        String messageCode = getViewDefinition().getPluginIdentifier() + "." + getViewDefinition().getName() + "." + getPath()
                + ".header";
        translationsMap.put(messageCode, getTranslationService().translate(messageCode, locale));
        for (ColumnDefinition column : columns.values()) {
            List<String> messageCodes = new LinkedList<String>();
            messageCodes.add(getViewDefinition().getPluginIdentifier() + "." + getViewDefinition().getName() + "." + getPath()
                    + ".column." + column.getName());
            messageCodes.add(getTranslationService().getEntityFieldMessageCode(getDataDefinition(), column.getName()));
            translationsMap.put(messageCodes.get(0), getTranslationService().translate(messageCodes, locale));
        }

    }

    private Object getFieldsForOptions(final Map<String, FieldDefinition> fields) {
        return new ArrayList<String>(fields.keySet());
    }

    private List<ColumnDefinition> getColumnsForOptions() {
        return new ArrayList<ColumnDefinition>(columns.values());
    }

    private HasManyType getHasManyType(final DataDefinition dataDefinition, final String fieldPath) {
        checkState(!fieldPath.matches("\\."), "Grid doesn't support sequential path");
        FieldDefinition fieldDefinition = dataDefinition.getField(fieldPath);
        if (fieldDefinition != null && fieldDefinition.getType() instanceof HasManyType) {
            return (HasManyType) fieldDefinition.getType();
        } else {
            throw new IllegalStateException("Grid data definition cannot be found");
        }
    }

    private ListData generateListData(final SearchResult rs, final String contextFieldName, final Long contextId) {
        List<Entity> entities = rs.getEntities();
        List<Entity> gridEntities = new LinkedList<Entity>();

        for (Entity entity : entities) {
            Entity gridEntity = new DefaultEntity(entity.getId());
            for (ColumnDefinition column : columns.values()) {
                gridEntity.setField(column.getName(), column.getValue(entity));
            }
            gridEntities.add(gridEntity);
        }

        int totalNumberOfEntities = rs.getTotalNumberOfEntities();
        return new ListData(totalNumberOfEntities, gridEntities, contextFieldName, contextId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Long getSelectedEntityId(final ViewValue<Long> viewValue) {
        ViewValue<ListData> value = (ViewValue<ListData>) lookupViewValue(viewValue);
        return value.getValue().getSelectedEntityId();
    }

}
