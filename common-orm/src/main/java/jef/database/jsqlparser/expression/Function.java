/*
 * JEF - Copyright 2009-2010 Jiyi (mr.jiyi@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jef.database.jsqlparser.expression;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import jef.database.jsqlparser.expression.operators.relational.ExpressionList;
import jef.database.jsqlparser.visitor.Expression;
import jef.database.jsqlparser.visitor.ExpressionType;
import jef.database.jsqlparser.visitor.ExpressionVisitor;
import jef.database.wrapper.clause.GroupFunctionType;

/**
 * A function as MAX,COUNT...
 */
public class Function implements Expression {
	public Expression rewrite;

	private String name;

	private ExpressionList parameters;

	private boolean allColumns = false;

	private boolean distinct = false;

	private boolean isEscaped = false;

	/**
	 * 当函数为分析函数时使用的开窗函数。
	 */
	private Over over;
	
	public Function() {
	}
	
	/**
	 * 开窗函数
	 * @return 开窗函数
	 */
	public Over getOver() {
		return over;
	}
	/**
	 * 设置开窗函数
	 * @param over 开窗函数
	 */
	public void setOver(Over over) {
		this.over = over;
	}

	public Function(String name, Expression... params) {
		this.name = name;
		if (params.length > 0) {
			this.parameters = new ExpressionList(params);
		}
	}

	public Function(String name, List<Expression> arguments) {
		this.name = name;
		this.parameters = new ExpressionList(arguments);
	}

	public void accept(ExpressionVisitor expressionVisitor) {
		if (rewrite == null) {
			expressionVisitor.visit(this);
		} else {
			rewrite.accept(expressionVisitor);
		}
	}

	/**
	 * The name of he function, i.e. "MAX"
	 * 
	 * @return the name of he function
	 */
	public String getName() {
		return name;
	}

	public void setName(String string) {
		name = string;
	}

	/**
	 * true if the parameter to the function is "*"
	 * 
	 * @return true if the parameter to the function is "*"
	 */
	public boolean isAllColumns() {
		return allColumns;
	}

	public void setAllColumns(boolean b) {
		allColumns = b;
	}

	/**
	 * true if the function is "distinct"
	 * 
	 * @return true if the function is "distinct"
	 */
	public boolean isDistinct() {
		return distinct;
	}

	public void setDistinct(boolean distinct) {
		this.distinct = distinct;
	}

	/**
	 * The list of parameters of the function (if any, else null) If the
	 * parameter is "*", allColumns is set to true
	 * 
	 * @return the list of parameters of the function (if any, else null)
	 */
	public ExpressionList getParameters() {
		return parameters;
	}

	public void setParameters(ExpressionList list) {
		parameters = list;
	}
	
	/**
	 * 是否为Oracle/MS-SQL分析函数
	 * @return
	 */
	public boolean isStatics(){
		return this.over!=null;
	}
	
	/**
	 * 获得参数个数
	 * @return
	 */
	public int getParamCount(){
		return parameters==null?0:parameters.size();
	}

	/**
	 * Return true if it's in the form "{fn function_body() }"
	 * 
	 * @return true if it's java-escaped
	 */
	public boolean isEscaped() {
		return isEscaped;
	}

	public void setEscaped(boolean isEscaped) {
		this.isEscaped = isEscaped;
	}

	public String toString() {
		StringBuilder sb=new StringBuilder(64);
		appendTo(sb);
		return sb.toString();
	}

	public void appendTo(StringBuilder sb) {
		if (rewrite != null) {
			rewrite.appendTo(sb);
			return;
		}
		if (isEscaped) {
			sb.append("{fn ");
		}
		sb.append(name);
		if(allColumns){
			sb.append("(*)");
		}else if(parameters!=null || over!=null){
			sb.append(isDistinct()?"(DISTINCT ":"(");
			if(parameters!=null){
				Iterator<Expression> iter=parameters.getExpressions().iterator();
				if(iter.hasNext()){
					iter.next().appendTo(sb);
				}
				String sep=parameters.getBetween();
				while(iter.hasNext()){
					sb.append(sep);
					iter.next().appendTo(sb);
				}	
			}
			sb.append(')');
		}
		if (isEscaped) {
			sb.append('}');
		}
		if(over!=null){
			over.appendTo(sb);
		}
	}

	public ExpressionType getType() {
		return ExpressionType.function;
	}
	
	static Map<String,GroupFunctionType> mapping=new HashMap<String,GroupFunctionType>();
	static{
		mapping.put("avg", GroupFunctionType.AVG);
		mapping.put("count", GroupFunctionType.COUNT);
		mapping.put("max", GroupFunctionType.MAX);
		mapping.put("min", GroupFunctionType.MIN);
		mapping.put("sum", GroupFunctionType.SUM);
		mapping.put("checksum", GroupFunctionType.CHECKSUM);
		mapping.put("checksum_agg", GroupFunctionType.CHECKSUM_AGG);
		mapping.put("countbig", GroupFunctionType.COUNT);
	}
	
	/**
	 * 获得聚合函数类型
	 * @return 如果是统计聚合函数，返回聚合函数类型，否则返回null
	 */
	public GroupFunctionType getGroupFunctionType(){
		return mapping.get(this.name.toLowerCase());
	}
}
