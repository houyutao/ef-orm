package jef.database.dialect;

import java.sql.Types;

import com.querydsl.sql.SQLServer2008Templates;
import com.querydsl.sql.SQLTemplates;

/**
 * SQL Server 2008
 * 
 * 2008特性 1、支持稀疏列 2、支持压缩表和压缩索引 3、新排序规则（collations） 4、分区切换 5、宽表、带有列族的表
 * 
 * 6、支持Date time datetime以及时区数据类型? 7支持 WITH ROLLUP, WITH CUBE, and ALL syntax is
 * deprecated. For more information, see Using GROUP BY with ROLLUP, CUBE, and
 * GROUPING SETS. 8 支持Merge语句 9 一个insert语句可以插入多行记录
 * 
 * 
 * @author jiyi
 * 
 */
public class SQLServer2008Dialect extends SQLServer2005Dialect {

	public SQLServer2008Dialect() {
		super();
		typeNames.put(Types.DATE, "date", 0);
		typeNames.put(Types.TIME, "time", 0);
		typeNames.put(Types.TIMESTAMP, "datetime2", 0);
	}
	
	 //to be override
    protected SQLTemplates generateQueryDslTemplates() {
        return new SQLServer2008Templates();
    }

}
