/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.sql.expression;

import org.elasticsearch.xpack.sql.SqlIllegalArgumentException;
import org.elasticsearch.xpack.sql.expression.gen.pipeline.Pipe;
import org.elasticsearch.xpack.sql.type.DataType;
import org.elasticsearch.xpack.sql.type.DataTypes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

public final class Expressions {

    public enum ParamOrdinal {
        DEFAULT,
        FIRST,
        SECOND,
        THIRD,
        FOURTH
    }

    private Expressions() {}

    public static NamedExpression wrapAsNamed(Expression exp) {
        return exp instanceof NamedExpression ? (NamedExpression) exp : new Alias(exp.source(), exp.sourceText(), exp);
    }

    public static List<Attribute> asAttributes(List<? extends NamedExpression> named) {
        if (named.isEmpty()) {
            return emptyList();
        }
        List<Attribute> list = new ArrayList<>(named.size());
        for (NamedExpression exp : named) {
            list.add(exp.toAttribute());
        }
        return list;
    }

    public static AttributeMap<Expression> asAttributeMap(List<? extends NamedExpression> named) {
        if (named.isEmpty()) {
            return new AttributeMap<>(emptyMap());
        }

        AttributeMap<Expression> map = new AttributeMap<>();
        for (NamedExpression exp : named) {
            map.add(exp.toAttribute(), exp);
        }
        return map;
    }

    public static boolean anyMatch(List<? extends Expression> exps, Predicate<? super Expression> predicate) {
        for (Expression exp : exps) {
            if (exp.anyMatch(predicate)) {
                return true;
            }
        }
        return false;
    }

    public static boolean match(List<? extends Expression> exps, Predicate<? super Expression> predicate) {
        for (Expression exp : exps) {
            if (predicate.test(exp)) {
                return true;
            }
        }
        return false;
    }

    public static Nullability nullable(List<? extends Expression> exps) {
        return Nullability.and(exps.stream().map(Expression::nullable).toArray(Nullability[]::new));
    }

    public static boolean foldable(List<? extends Expression> exps) {
        for (Expression exp : exps) {
            if (!exp.foldable()) {
                return false;
            }
        }
        return true;
    }

    public static AttributeSet references(List<? extends Expression> exps) {
        if (exps.isEmpty()) {
            return AttributeSet.EMPTY;
        }

        AttributeSet set = new AttributeSet();
        for (Expression exp : exps) {
            set.addAll(exp.references());
        }
        return set;
    }

    public static AttributeSet filterReferences(Set<? extends Expression> exps, AttributeSet excluded) {
        AttributeSet ret = new AttributeSet();
        while (exps.size() > 0) {

            Set<Expression> filteredExps = new LinkedHashSet<>();
            for (Expression exp : exps) {
                Expression attr = Expressions.attribute(exp);
                if (attr == null || (excluded.contains(attr) == false)) {
                    filteredExps.add(exp);
                }
            }

            ret.addAll(new AttributeSet(
                filteredExps.stream().filter(c->c.children().isEmpty())
                .flatMap(exp->exp.references().stream())
                .collect(Collectors.toSet())
            ));

            exps = filteredExps.stream()
                .flatMap((Expression exp)->exp.children().stream())
                .collect(Collectors.toSet());
        }
        return ret;
    }

    public static String name(Expression e) {
        return e instanceof NamedExpression ? ((NamedExpression) e).name() : e.nodeName();
    }

    public static boolean isNull(Expression e) {
        return e.dataType() == DataType.NULL || (e.foldable() && e.fold() == null);
    }

    public static List<String> names(Collection<? extends Expression> e) {
        List<String> names = new ArrayList<>(e.size());
        for (Expression ex : e) {
            names.add(name(ex));
        }

        return names;
    }

    public static Attribute attribute(Expression e) {
        if (e instanceof NamedExpression) {
            return ((NamedExpression) e).toAttribute();
        }
        if (e != null && e.foldable()) {
            return Literal.of(e).toAttribute();
        }
        return null;
    }

    public static boolean equalsAsAttribute(Expression left, Expression right) {
        if (!left.semanticEquals(right)) {
            Attribute l = attribute(left);
            return (l != null && l.semanticEquals(attribute(right)));
        }
        return true;
    }

    public static List<Attribute> onlyPrimitiveFieldAttributes(Collection<Attribute> attributes) {
        List<Attribute> filtered = new ArrayList<>();
        // add only primitives
        // but filter out multi fields (allow only the top-level value)
        Set<Attribute> seenMultiFields = new LinkedHashSet<>();

        for (Attribute a : attributes) {
            if (!DataTypes.isUnsupported(a.dataType()) && a.dataType().isPrimitive()) {
                if (a instanceof FieldAttribute) {
                    FieldAttribute fa = (FieldAttribute) a;
                    // skip nested fields and seen multi-fields
                    if (!fa.isNested() && !seenMultiFields.contains(fa.parent())) {
                        filtered.add(a);
                        seenMultiFields.add(a);
                    }
                } else {
                    filtered.add(a);
                }
            }
        }

        return filtered;
    }

    public static Pipe pipe(Expression e) {
        if (e instanceof NamedExpression) {
            return ((NamedExpression) e).asPipe();
        }
        throw new SqlIllegalArgumentException("Cannot create pipe for {}", e);
    }

    public static List<Pipe> pipe(List<Expression> expressions) {
        List<Pipe> pipes = new ArrayList<>(expressions.size());
        for (Expression e : expressions) {
            pipes.add(pipe(e));
        }
        return pipes;
    }
}
