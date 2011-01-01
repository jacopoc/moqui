/*
 * This Work is in the public domain and is provided on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE,
 * NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 * You are solely responsible for determining the appropriateness of using
 * this Work and assume any risks associated with your use of this Work.
 *
 * This Work includes contributions authored by David E. Jones, not as a
 * "work for hire", who hereby disclaims any copyright to the same.
 */
package org.moqui.impl.entity

import java.sql.Timestamp

import org.moqui.entity.EntityConditionFactory
import org.moqui.entity.EntityCondition
import org.moqui.entity.EntityCondition.JoinOperator
import org.moqui.entity.EntityCondition.ComparisonOperator
import org.moqui.impl.entity.EntityFindBuilder.EntityConditionParameter
import org.moqui.impl.StupidUtilities

class EntityConditionFactoryImpl implements EntityConditionFactory {
    protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EntityConditionFactoryImpl.class)

    protected final EntityFacadeImpl efi;

    EntityConditionFactoryImpl(EntityFacadeImpl efi) {
        this.efi = efi;
    }

    /** @see org.moqui.entity.EntityConditionFactory#makeCondition(EntityCondition, JoinOperator, EntityCondition) */
    EntityCondition makeCondition(EntityCondition lhs, JoinOperator operator, EntityCondition rhs) {
        return new BasicJoinCondition(this, (EntityConditionImplBase) lhs, operator, (EntityConditionImplBase) rhs)
    }

    /** @see org.moqui.entity.EntityConditionFactory#makeCondition(String, ComparisonOperator, Object) */
    EntityCondition makeCondition(String fieldName, ComparisonOperator operator, Object value) {
        return new FieldValueCondition(this, new ConditionField(fieldName), operator, value)
    }

    /** @see org.moqui.entity.EntityConditionFactory#makeConditionToField(String, ComparisonOperator, String) */
    EntityCondition makeConditionToField(String fieldName, ComparisonOperator operator, String toFieldName) {
        return new FieldToFieldCondition(this, new ConditionField(fieldName), operator, new ConditionField(toFieldName))
    }

    /** @see org.moqui.entity.EntityConditionFactory#makeCondition(List<EntityCondition>, JoinOperator) */
    EntityCondition makeCondition(List<EntityCondition> conditionList, JoinOperator operator) {
        return new ListCondition(this, (List<EntityConditionImplBase>) conditionList, operator)
    }

    /** @see org.moqui.entity.EntityConditionFactory#makeCondition(List<EntityCondition>) */
    EntityCondition makeCondition(List<EntityCondition> conditionList) {
        return new ListCondition(this, (List<EntityConditionImplBase>) conditionList, JoinOperator.AND)
    }

    /** @see org.moqui.entity.EntityConditionFactory#makeCondition(Map<String,?>, ComparisonOperator, JoinOperator) */
    EntityCondition makeCondition(Map<String, ?> fieldMap, ComparisonOperator comparisonOperator, JoinOperator joinOperator) {
        return new MapCondition(this, fieldMap, comparisonOperator, joinOperator)
    }

    /** @see org.moqui.entity.EntityConditionFactory#makeCondition(Map<String,?>) */
    EntityCondition makeCondition(Map<String, ?> fieldMap) {
        return new MapCondition(this, fieldMap, ComparisonOperator.EQUALS, JoinOperator.AND)
    }

    /** @see org.moqui.entity.EntityConditionFactory#makeConditionDate(String, String, Timestamp) */
    EntityCondition makeConditionDate(String fromFieldName, String thruFieldName, Timestamp compareStamp) {
        return new DateCondition(this, fromFieldName, thruFieldName, compareStamp)
    }

    /** @see org.moqui.entity.EntityConditionFactory#makeConditionWhere(String) */
    EntityCondition makeConditionWhere(String sqlWhereClause) {
        return new WhereCondition(this, sqlWhereClause)
    }

    public static abstract class EntityConditionImplBase implements EntityCondition {
        EntityConditionFactoryImpl ecFactoryImpl;

        EntityConditionImplBase(EntityConditionFactoryImpl ecFactoryImpl) {
            this.ecFactoryImpl = ecFactoryImpl;
        }

        /** Build SQL Where text to evaluate condition in a database. */
        public abstract void makeSqlWhere(EntityFindBuilder efb);
    }

    public static class BasicJoinCondition extends EntityConditionImplBase {
        protected EntityConditionImplBase lhs
        protected JoinOperator operator
        protected EntityConditionImplBase rhs

        BasicJoinCondition(EntityConditionFactoryImpl ecFactoryImpl,
                EntityConditionImplBase lhs, JoinOperator operator, EntityConditionImplBase rhs) {
            super(ecFactoryImpl)
            this.lhs = lhs
            this.operator = operator ? operator : JoinOperator.AND
            this.rhs = rhs
        }

        void makeSqlWhere(EntityFindBuilder efb) {
            StringBuilder sql = efb.getSqlTopLevel()
            sql.append('(')
            this.lhs.makeSqlWhere(efb)
            sql.append(' ')
            sql.append(StupidUtilities.getJoinOperatorString(this.operator))
            sql.append(' ')
            this.rhs.makeSqlWhere(efb)
            sql.append(')')
        }

        boolean mapMatches(Map<String, ?> map) {
            boolean lhsMatches = this.lhs.mapMatches(map)

            // handle cases where we don't need to evaluate rhs
            if (lhsMatches && operator == JoinOperator.OR) return true
            if (!lhsMatches && operator == JoinOperator.AND) return false

            // handle opposite cases since we know cases above aren't true (ie if OR then lhs=false, if AND then lhs=true
            // if rhs then result is true whether AND or OR
            // if !rhs then result is false whether AND or OR
            return this.rhs.mapMatches(map)
        }

        EntityCondition ignoreCase() { throw new IllegalArgumentException("Ignore case not supported for this type of condition.") }

        String toString() {
            // general SQL where clause style text with values included
            return "(" + lhs.toString() + " " + StupidUtilities.getJoinOperatorString(this.operator) + " " + rhs.toString() + ")"
        }

        @Override
        int hashCode() {
            // TODO override for use as a cache key
            return super.hashCode()
        }

        @Override
        boolean equals(Object o) {
            if (!(o instanceof BasicJoinCondition)) return false
            BasicJoinCondition that = (BasicJoinCondition) o
            if (!this.lhs.equals(that.lhs)) return false
            if (this.operator != that.operator) return false
            if (!this.rhs.equals(that.rhs)) return false
            return true
        }
    }

    public static class FieldValueCondition extends EntityConditionImplBase {
        protected ConditionField field
        protected ComparisonOperator operator
        protected Object value
        protected boolean ignoreCase = false

        FieldValueCondition(EntityConditionFactoryImpl ecFactoryImpl,
                ConditionField field, ComparisonOperator operator, Object value) {
            super(ecFactoryImpl)
            this.field = field
            this.operator = operator ? operator : JoinOperator.AND
            this.value = value
        }

        void makeSqlWhere(EntityFindBuilder efb) {
            StringBuilder sql = efb.getSqlTopLevel()
            if (this.ignoreCase) sql.append("UPPER(")
            sql.append(efb.mainEntityDefinition.getColumnName(this.fieldName, false))
            if (this.ignoreCase) sql.append(')')
            sql.append(' ')
            boolean valueDone = false
            if (this.value == null) {
                if (this.operator == ComparisonOperator.EQUALS || this.operator == ComparisonOperator.LIKE ||
                        this.operator == ComparisonOperator.IN) {
                    sql.append(" IS NULL")
                    valueDone = true
                } else if (this.operator == ComparisonOperator.NOT_EQUAL || this.operator == ComparisonOperator.NOT_LIKE ||
                        this.operator == ComparisonOperator.NOT_IN) {
                    sql.append(" IS NOT NULL")
                    valueDone = true
                }
            }
            if (!valueDone) {
                sql.append(StupidUtilities.getComparisonOperatorString(this.operator))
                if ((this.operator == ComparisonOperator.IN || this.operator == ComparisonOperator.NOT_IN) &&
                        this.value instanceof Collection) {
                    sql.append(" (")
                    boolean isFirst = true
                    for (Object curValue in this.value) {
                        if (isFirst) isFirst = false else sql.append(", ")
                        sql.append("?")
                        if (this.ignoreCase && curValue instanceof String) curValue = ((String) curValue).toUpperCase()
                        efb.getParameters().add(new EntityConditionParameter(efb.mainEntityDefinition.getFieldNode(this.fieldName), curValue, efb))
                    }
                    sql.append(')')
                } else if (this.operator == ComparisonOperator.BETWEEN &&
                        this.value instanceof Collection && ((Collection) this.value).size() == 2) {
                    sql.append(" ? AND ?")
                    Iterator iterator = ((Collection) this.value).iterator()
                    Object value1 = iterator.next()
                    if (this.ignoreCase && value1 instanceof String) value1 = ((String) value1).toUpperCase()
                    Object value2 = iterator.next()
                    if (this.ignoreCase && value2 instanceof String) value2 = ((String) value2).toUpperCase()
                    efb.getParameters().add(new EntityConditionParameter(efb.mainEntityDefinition.getFieldNode(this.fieldName), value1, efb))
                    efb.getParameters().add(new EntityConditionParameter(efb.mainEntityDefinition.getFieldNode(this.fieldName), value2, efb))
                } else {
                    if (this.ignoreCase && this.value instanceof String) this.value = ((String) this.value).toUpperCase()
                    sql.append(" ?")
                    efb.getParameters().add(new EntityConditionParameter(efb.mainEntityDefinition.getFieldNode(this.fieldName), this.value, efb))
                }
            }
        }

        boolean mapMatches(Map<String, ?> map) {
            Object value1 = map.get(this.fieldName)
            return StupidUtilities.compareByOperator(value1, this.operator, this.value)
        }

        EntityCondition ignoreCase() { this.ignoreCase = true; return this }

        String toString() {
            return this.fieldName + " " + StupidUtilities.getComparisonOperatorString(this.operator) + " " + this.value
        }

        @Override
        int hashCode() {
            // TODO override for use as a cache key
            return super.hashCode()
        }

        @Override
        boolean equals(Object o) {
            if (!(o instanceof FieldValueCondition)) return false
            FieldValueCondition that = (FieldValueCondition) o
            if (!this.field.equals(that.field)) return false
            if (this.operator != that.operator) return false
            if (!this.value.equals(that.value)) return false
            if (this.ignoreCase != that.ignoreCase) return false
            return true
        }
    }

    public static class FieldToFieldCondition extends EntityConditionImplBase {
        protected ConditionField field
        protected ComparisonOperator operator
        protected ConditionField toField
        protected boolean ignoreCase = false

        FieldToFieldCondition(EntityConditionFactoryImpl ecFactoryImpl,
                ConditionField field, ComparisonOperator operator, ConditionField toField) {
            super(ecFactoryImpl)
            this.field = field
            this.operator = operator ? operator : JoinOperator.AND
            this.toField = toField
        }

        void makeSqlWhere(EntityFindBuilder efb) {
            StringBuilder sql = efb.getSqlTopLevel()
            if (this.ignoreCase) sql.append("UPPER(")
            sql.append(efb.mainEntityDefinition.getColumnName(this.fieldName, false))
            if (this.ignoreCase) sql.append(")")
            sql.append(' ')
            sql.append(StupidUtilities.getComparisonOperatorString(this.operator))
            sql.append(' ')
            if (this.ignoreCase) sql.append("UPPER(")
            sql.append(efb.mainEntityDefinition.getColumnName(this.toFieldName, false))
            if (this.ignoreCase) sql.append(")")
        }

        boolean mapMatches(Map<String, ?> map) {
            Object value1 = map.get(this.fieldName)
            Object value2 = map.get(this.toFieldName)
            return StupidUtilities.compareByOperator(value1, this.operator, value2)
        }

        EntityCondition ignoreCase() { this.ignoreCase = true; return this }

        String toString() {
            return this.fieldName + " " + StupidUtilities.getComparisonOperatorString(this.operator) + " " + this.toFieldName
        }

        @Override
        int hashCode() {
            // TODO override for use as a cache key
            return super.hashCode()
        }

        @Override
        boolean equals(Object o) {
            if (!(o instanceof FieldToFieldCondition)) return false
            FieldToFieldCondition that = (FieldToFieldCondition) o
            if (!this.field.equals(that.field)) return false
            if (this.operator != that.operator) return false
            if (!this.toField.equals(that.toField)) return false
            if (this.ignoreCase != that.ignoreCase) return false
            return true
        }
    }

    public static class ListCondition extends EntityConditionImplBase {
        protected List<EntityConditionImplBase> conditionList
        protected JoinOperator operator

        ListCondition(EntityConditionFactoryImpl ecFactoryImpl,
                List<EntityConditionImplBase> conditionList, JoinOperator operator) {
            super(ecFactoryImpl)
            this.conditionList = conditionList ? conditionList : new LinkedList()
            this.operator = operator ? operator : JoinOperator.AND
        }

        void makeSqlWhere(EntityFindBuilder efb) {
            if (!this.conditionList) return

            StringBuilder sql = efb.getSqlTopLevel()
            sql.append('(')
            boolean isFirst = true
            for (EntityConditionImplBase condition in this.conditionList) {
                if (isFirst) isFirst = false else {
                    sql.append(' ')
                    sql.append(StupidUtilities.getJoinOperatorString(this.operator))
                    sql.append(' ')
                }
                condition.makeSqlWhere(efb)
            }
        }

        boolean mapMatches(Map<String, ?> map) {
            for (EntityConditionImplBase condition in this.conditionList) {
                boolean conditionMatches = condition.mapMatches(map)
                if (conditionMatches && this.operator == JoinOperator.OR) return true
                if (!conditionMatches && this.operator == JoinOperator.AND) return false
            }
            // if we got here it means that it's an OR with no trues, or an AND with no falses
            return (this.operator == JoinOperator.AND)
        }

        EntityCondition ignoreCase() { throw new IllegalArgumentException("Ignore case not supported for this type of condition.") }

        String toString() {
            StringBuilder sb = new StringBuilder()
            for (EntityConditionImplBase condition in this.conditionList) {
                if (sb.length() > 0) {
                    sb.append(' ')
                    sb.append(StupidUtilities.getJoinOperatorString(this.operator))
                    sb.append(' ')
                }
                sb.append(condition.toString())
            }
            return sb.toString()
        }

        @Override
        int hashCode() {
            // TODO override for use as a cache key
            return super.hashCode()
        }

        @Override
        boolean equals(Object o) {
            if (!(o instanceof ListCondition)) return false
            ListCondition that = (ListCondition) o
            if (this.operator != that.operator) return false
            if (!this.conditionList.equals(that.conditionList)) return false
            return true
        }
    }

    public static class MapCondition extends EntityConditionImplBase {
        protected Map<String, ?> fieldMap
        protected ComparisonOperator comparisonOperator
        protected JoinOperator joinOperator
        protected boolean ignoreCase = false

        MapCondition(EntityConditionFactoryImpl ecFactoryImpl,
                Map<String, ?> fieldMap, ComparisonOperator comparisonOperator, JoinOperator joinOperator) {
            super(ecFactoryImpl)
            this.fieldMap = fieldMap ? fieldMap : new HashMap()
            this.comparisonOperator = comparisonOperator ? comparisonOperator : ComparisonOperator.EQUALS
            this.joinOperator = joinOperator ? joinOperator : JoinOperator.AND
        }

        void makeSqlWhere(EntityFindBuilder efb) {
            this.makeCondition().makeSqlWhere(efb)
        }

        boolean mapMatches(Map<String, ?> map) {
            return this.makeCondition().mapMatches(map)
        }

        EntityCondition ignoreCase() { this.ignoreCase = true; return this }

        String toString() {
            return this.makeCondition().toString()
            /* might want to do something like this at some point, but above is probably better for now
            StringBuilder sb = new StringBuilder()
            for (Map.Entry fieldEntry in this.fieldMap.entrySet()) {
                if (sb.length() > 0) {
                    sb.append(' ')
                    sb.append(StupidUtilities.getJoinOperatorString(this.joinOperator))
                    sb.append(' ')
                }
                sb.append(fieldEntry.getKey())
                sb.append(' ')
                sb.append(StupidUtilities.getComparisonOperatorString(this.comparisonOperator))
                sb.append(' ')
                sb.append(fieldEntry.getValue())
            }
            return sb.toString()
            */
        }

        protected EntityConditionImplBase makeCondition() {
            List conditionList = new LinkedList()
            for (Map.Entry<String, ?> fieldEntry in this.fieldMap.entrySet()) {
                EntityConditionImplBase newCondition = this.ecFactoryImpl.makeCondition(fieldEntry.getKey(),
                        this.comparisonOperator, fieldEntry.getValue())
                if (this.ignoreCase) newCondition.ignoreCase()
                conditionList.add(newCondition)
            }
            return this.ecFactoryImpl.makeCondition(conditionList, this.joinOperator)
        }

        @Override
        int hashCode() {
            // TODO override for use as a cache key
            return super.hashCode()
        }

        @Override
        boolean equals(Object o) {
            if (!(o instanceof MapCondition)) return false
            MapCondition that = (MapCondition) o
            if (this.comparisonOperator != that.comparisonOperator) return false
            if (this.joinOperator != that.joinOperator) return false
            if (this.ignoreCase != that.ignoreCase) return false
            if (!this.fieldMap.equals(that.fieldMap)) return false
            return true
        }
    }

    public static class DateCondition extends EntityConditionImplBase {
        protected String fromFieldName
        protected String thruFieldName
        protected Timestamp compareStamp

        DateCondition(EntityConditionFactoryImpl ecFactoryImpl,
                String fromFieldName, String thruFieldName, Timestamp compareStamp) {
            super(ecFactoryImpl)
            this.fromFieldName = fromFieldName ? fromFieldName : "fromDate"
            this.thruFieldName = thruFieldName ? thruFieldName : "thruDate"
            this.compareStamp = compareStamp
        }

        void makeSqlWhere(EntityFindBuilder efb) {
            this.makeCondition().makeSqlWhere(efb)
        }

        boolean mapMatches(Map<String, ?> map) {
            return this.makeCondition().mapMatches(map)
        }

        EntityCondition ignoreCase() { throw new IllegalArgumentException("Ignore case not supported for this type of condition.") }

        String toString() {
            return this.makeCondition().toString()
        }

        protected EntityConditionImplBase makeCondition() {
            return this.ecFactoryImpl.makeCondition(
                this.ecFactoryImpl.makeCondition(
                    this.ecFactoryImpl.makeCondition(this.thruFieldName, ComparisonOperator.EQUALS, null),
                    JoinOperator.OR,
                    this.ecFactoryImpl.makeCondition(this.thruFieldName, ComparisonOperator.GREATER_THAN, this.compareStamp)
                ),
                JoinOperator.AND,
                this.ecFactoryImpl.makeCondition(
                    this.ecFactoryImpl.makeCondition(this.fromFieldName, ComparisonOperator.EQUALS, null),
                    JoinOperator.OR,
                    this.ecFactoryImpl.makeCondition(this.fromFieldName, ComparisonOperator.LESS_THAN_EQUAL_TO, this.compareStamp)
                )
            )
        }

        @Override
        int hashCode() {
            // TODO override for use as a cache key
            return super.hashCode()
        }

        @Override
        boolean equals(Object o) {
            if (!(o instanceof DateCondition)) return false
            DateCondition that = (DateCondition) o
            if (!this.fromFieldName.equals(that.fromFieldName)) return false
            if (!this.thruFieldName.equals(that.thruFieldName)) return false
            if (this.compareStamp != that.compareStamp) return false
            return true
        }
    }

    public static class WhereCondition extends EntityConditionImplBase {
        protected String sqlWhereClause

        WhereCondition(EntityConditionFactoryImpl ecFactoryImpl, String sqlWhereClause) {
            super(ecFactoryImpl)
            this.sqlWhereClause = sqlWhereClause ? sqlWhereClause : ""
        }

        void makeSqlWhere(EntityFindBuilder efb) {
            efb.getSqlTopLevel().append(this.sqlWhereClause)
        }

        boolean mapMatches(Map<String, ?> map) {
            // NOTE: always return false unless we eventually implement some sort of SQL parsing, for caching/etc
            // always consider not matching
            logger.warn("The mapMatches for the SQL Where Condition is not supported, text is [${this.sqlWhereClause}]")
            return false
        }

        EntityCondition ignoreCase() { throw new IllegalArgumentException("Ignore case not supported for this type of condition.") }

        String toString() {
            return this.sqlWhereClause
        }

        @Override
        int hashCode() {
            // TODO override for use as a cache key
            return super.hashCode()
        }

        @Override
        boolean equals(Object o) {
            if (!(o instanceof WhereCondition)) return false
            WhereCondition that = (WhereCondition) o
            if (!this.sqlWhereClause.equals(that.sqlWhereClause)) return false
            return true
        }
    }

    protected static class ConditionField {
        String entityAlias = null
        String fieldName
        EntityDefinition aliasEntityDef = null

        ConditionField(String fieldName) {
            this.fieldName = fieldName
        }
        ConditionField(String entityAlias, String fieldName, EntityDefinition aliasEntityDef) {
            this.entityAlias = entityAlias
            this.fieldName = fieldName
            this.aliasEntityDef = aliasEntityDef
        }

        String getColumnName(EntityDefinition ed) {
            StringBuilder colName = new StringBuilder()
            // NOTE: this could have issues with view-entities as member entities where they have functions/etc; we may
            // have to pass the prefix in to have it added inside functions/etc
            if (this.entityAlias) colName.append(this.entityAlias).append('.')
            if (this.aliasEntityDef) {
                colName.append(this.aliasEntityDef.getColumnName(this.fieldName, false))
            } else {
                colName.append(ed.getColumnName(this.fieldName, false))
            }
            return colName.toString()
        }

        @Override
        int hashCode() {
            // TODO override for use as a cache key
            return super.hashCode()
        }

        @Override
        boolean equals(Object o) {
            if (!(o instanceof ConditionField)) return false
            ConditionField that = (ConditionField) o
            if (this.entityAlias != that.entityAlias) return false
            if (this.aliasEntityDef != that.aliasEntityDef) return false
            if (!this.fieldName.equals(that.fieldName)) return false
            return true
        }
    }
}
