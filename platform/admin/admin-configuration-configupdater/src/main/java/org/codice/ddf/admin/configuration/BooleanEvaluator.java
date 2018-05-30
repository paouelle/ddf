/**
 * Copyright (c) Codice Foundation
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.ddf.admin.configuration;

import com.fathzer.soft.javaluator.AbstractEvaluator;
import com.fathzer.soft.javaluator.BracketPair;
import com.fathzer.soft.javaluator.Constant;
import com.fathzer.soft.javaluator.Operator;
import com.fathzer.soft.javaluator.Parameters;
import java.util.Iterator;

public class BooleanEvaluator extends AbstractEvaluator<Boolean> {
  public static final Operator NOT = new Operator("!", 1, Operator.Associativity.RIGHT, 3);
  public static final Operator AND = new Operator("&&", 2, Operator.Associativity.LEFT, 2);
  public static final Operator OR = new Operator("||", 2, Operator.Associativity.LEFT, 1);

  public static final Constant TRUE = new Constant("true");
  public static final Constant FALSE = new Constant("false");

  private static final Parameters PARAMETERS = new Parameters();

  static {
    PARAMETERS.add(BooleanEvaluator.NOT);
    PARAMETERS.add(BooleanEvaluator.AND);
    PARAMETERS.add(BooleanEvaluator.OR);
    PARAMETERS.add(BooleanEvaluator.TRUE);
    PARAMETERS.add(BooleanEvaluator.FALSE);
    PARAMETERS.addExpressionBracket(BracketPair.PARENTHESES);
  }

  public BooleanEvaluator() {
    super(BooleanEvaluator.PARAMETERS);
  }

  @Override
  protected Boolean toValue(String literal, Object context) {
    return Boolean.valueOf(literal);
  }

  @Override
  protected Boolean evaluate(Constant constant, Object evaluationContext) {
    if (BooleanEvaluator.TRUE.equals(constant)) {
      return true;
    } else if (BooleanEvaluator.FALSE.equals(constant)) {
      return false;
    }
    return super.evaluate(constant, evaluationContext);
  }

  @Override
  @SuppressWarnings(
      "IdentityBinaryExpression" /* both operands are retrieved from an iterator so they are different */)
  protected Boolean evaluate(
      Operator operator, Iterator<Boolean> operands, Object evaluationContext) {

    if (BooleanEvaluator.NOT.equals(operator)) {
      return !operands.next();
    } else if (BooleanEvaluator.AND.equals(operator)) {
      return operands.next() && operands.next();
    } else if (BooleanEvaluator.OR.equals(operator)) {
      return operands.next() || operands.next();
    }
    return super.evaluate(operator, operands, evaluationContext);
  }
}
