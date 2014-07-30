package jef.database.dialect.type;

import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import javax.persistence.GenerationType;
import javax.persistence.SequenceGenerator;
import javax.persistence.TableGenerator;

import jef.common.Entry;
import jef.database.AutoIncreatmentCallBack.JdbcAutoGeneratedKeyCallback;
import jef.database.AutoIncreatmentCallBack.SequenceGenerateCallback;
import jef.database.DbUtils;
import jef.database.Field;
import jef.database.IQueryableEntity;
import jef.database.ORMConfig;
import jef.database.OperateTarget;
import jef.database.Sequence;
import jef.database.annotation.PartitionFunction;
import jef.database.annotation.PartitionKey;
import jef.database.dialect.ColumnType;
import jef.database.dialect.ColumnType.AutoIncrement;
import jef.database.dialect.DatabaseDialect;
import jef.database.meta.Feature;
import jef.database.meta.ITableMetadata;
import jef.database.meta.MetaHolder;
import jef.database.wrapper.InsertSqlResult;
import jef.tools.StringUtils;
import jef.tools.reflect.Property;

/**
 * 自增的映射实现
 * 
 * @author jiyi
 * 
 * @param <T>
 */
public abstract class AutoIncrementMapping<T> extends ATypeMapping<T> {
	protected Property accessor;
	private int len;
	private boolean isBig;
	
	//缓存的计算结果
	private transient GenerationType generationType;
	private transient String[] sequenceName;
	private transient boolean guessMode;
	private transient boolean supportKeywordDefault;
	private transient JdbcAutoGeneratedKeyCallback autoGenerateCall;

	@Override
	public void init(Field field, String columnName, ColumnType type, ITableMetadata meta) {
		super.init(field, columnName, type, meta);
		len = ((AutoIncrement) type).getLength();
		this.isBig = len > 10;
	}
	
	/**
	 * 返回Sequence所在的数据源的名称（重定向已计算）
	 * @return 
	 * <li>null表示缺省数据库（表在哪里，Sequence就在哪里。）</li>
	 * <li>""表示使用默认数据源。</li>
	 */
	public String getSequenceDataSource(DatabaseDialect profile){
		if (bindedProfile!=profile || sequenceName == null) {
			String name = profile.getColumnNameIncase(rawColumnName);
			rebind(DbUtils.escapeColumn(profile, name),profile);
		}
		return sequenceName[0];
	}


	/**
	 * 返回Sequence的名称
	 * @return
	 */
	public String getSequenceName(DatabaseDialect profile) {
		if (bindedProfile!=profile || sequenceName == null) {
			String name = profile.getColumnNameIncase(rawColumnName);
			rebind(DbUtils.escapeColumn(profile, name),profile);
		}
		return sequenceName[1];
	}
	
	/*
	 * 计算生成策略
	 */
	@Override
	protected void rebind(String escapedColumn, DatabaseDialect profile) {
		super.rebind(escapedColumn, profile);
		
		AutoIncrement a = (AutoIncrement) ctype;
		GenerationType type = a.getGenerationType(profile, this.meta.getEffectPartitionKeys() == null);// 只有非分表的类允许使用Identity方式生成，其他都仅允许Seq或Tble
		generationType=type;
		sequenceName = getSequenceName0(meta.getSchema(), meta.getTableName(false),type);		
		guessMode=profile.has(Feature.BATCH_GENERATED_KEY_ONLY_LAST);
		
		autoGenerateCall=new JdbcAutoGeneratedKeyCallback(accessor, guessMode, getColumnName(profile, false));
		supportKeywordDefault=profile.notHas(Feature.NOT_SUPPORT_KEYWORD_DEFAULT);
	}

	/**
	 * 
	 * @param profile
	 * @return
	 */
	public GenerationType getGenerationType(DatabaseDialect profile) {
		if (profile != bindedProfile  || sequenceName == null ) {
			String name = profile.getColumnNameIncase(rawColumnName);
			rebind(DbUtils.escapeColumn(profile, name),profile);
		}
		return generationType;
	}

	private String[] getSequenceName0(String schema, String tableName,GenerationType gtype) {
		AutoIncrement type = (AutoIncrement) ctype;
		SequenceGenerator sg = type.getSeqGenerator();
		//多数据源下，数据源必须计算得到，不能用null表示
		boolean isMultiDatasource=isTableOnMultipleDataSources();
		
		if(gtype!=GenerationType.TABLE){
			if (sg != null && StringUtils.isNotEmpty(sg.sequenceName())) {
				if (StringUtils.isEmpty(schema)) {
					schema = sg.schema();
				}
				String datasource = getDataSource(sg,isMultiDatasource);
				if (StringUtils.isNotEmpty(schema)) {
					schema = MetaHolder.getMappingSchema(schema);
					return new String[] { datasource, schema + "." + sg.sequenceName() };
				} else {
					return new String[] { datasource, sg.sequenceName() };
				}
			}
		}
		if(gtype!=GenerationType.SEQUENCE){
			TableGenerator tg = type.getTableGenerator();
			if (tg != null && StringUtils.isNotEmpty(tg.table())) {
				if (StringUtils.isEmpty(schema)) {
					schema = tg.schema();
				}
				String datasource = getDataSource(tg,isMultiDatasource);
				if (StringUtils.isNotEmpty(schema)) {
					schema = MetaHolder.getMappingSchema(schema);
					return new String[] { datasource,schema + "." + tg.table() };
				} else {
					return new String[] { datasource,tg.table() };
				}
			}			
		}
		//即便不使用Seq，sequenceName也必须有值，否则会反复计算
		return new String[]{isMultiDatasource?"":null,DbUtils.calcSeqNameByTable(schema, tableName, this.rawColumnName)};
	}

	private boolean isTableOnMultipleDataSources() {
		boolean multiDb=false;
		if(meta.getEffectPartitionKeys()!=null){
			for(Entry<PartitionKey,PartitionFunction<?>> pk:meta.getEffectPartitionKeys()){
				if(pk.getKey().isDbName()){
					multiDb=true;
					break;
				}
			}
		}
		return multiDb && !ORMConfig.getInstance().isSingleSite();
	}

	/**
	 * Use the field catalog to specify the name of 'datasource'. (though I know the field doesn't mean this in JPA)
	 * @param tg
	 * @param partition
	 * @return  if single site, return null. or return the name of datasource for multiple site.
	 */
	private String getDataSource(TableGenerator tg,boolean partition) {
		if(!partition)return null;
		String datasource="";
		if(tg!=null){
			datasource=MetaHolder.getMappingSite(tg.catalog());
		}
		return datasource==null?"":datasource;
	}

	/**
	 * Use the field catalog to specify the name of 'datasource'. (though I know the field doesn't mean this in JPA)
	 * @param sg
	 * @param partition
	 * @return  if single site, return null. or return the name of datasource for multiple site.
	 */
	private String getDataSource(SequenceGenerator sg,boolean partition) {
		if(!partition)return null;
		String datasource="";
		if(sg!=null){
			datasource=MetaHolder.getMappingSite(sg.catalog());
		}
		return datasource==null?"":datasource;
	}

	@Override
	public void processInsert(Object value, InsertSqlResult result, List<String> cStr, List<String> vStr, boolean smart, IQueryableEntity obj) throws SQLException {
		DatabaseDialect profile = result.profile;
		Field field = this.field;
		
		//核对和刷新生成策略，后续操作对象许多都是从当前对象缓存结果中获取的。所以先刷新一下
		GenerationType type = getGenerationType(profile);
		// 手动指定
		if (isAssignedSequence(value) && ORMConfig.getInstance().isManualSequence() && obj.isUsed(field)) {
			cStr.add(rawColumnName);
			vStr.add(value.toString());
			return;
		}
	
		if (type == GenerationType.IDENTITY) {
			if (profile.has(Feature.NOT_SUPPORT_KEYWORD_DEFAULT)) {
				result.setCallback(autoGenerateCall);
			} else {
				cStr.add(rawColumnName);
				vStr.add("DEFAULT");
				result.setCallback(autoGenerateCall);
			}
		} else {
			String dbKey = result.getTableNames() == null ? null : result.getTableNames().getDatabase();
			OperateTarget db = new OperateTarget(result.parent, dbKey);
			Sequence seq = db.getSequence(this);
			result.setCallback(autoGenerateCall);
			cStr.add(cachedEscapeColumnName);
			vStr.add(seq.getName() + ".nextval");
		}
	}

	@Override
	public void processPreparedInsert(IQueryableEntity obj, List<String> cStr, List<String> vStr, InsertSqlResult result, boolean smart) throws SQLException {
		DatabaseDialect profile = result.profile;
		Field field = this.field;
		
		//核对和刷新生成策略，后续操作对象许多都是从当前对象缓存结果中获取的。所以先刷新一下
		GenerationType gType = getGenerationType(profile);
		// 手动指定
		if (obj.isUsed(field)&& ORMConfig.getInstance().isManualSequence()  && isAssignedSequence(accessor.get(obj))) {
			cStr.add(cachedEscapeColumnName);
			vStr.add("?");
			result.addField(field);
			return;
		}
		
		//是否需要返回自增值
		boolean returnKeys=!(ORMConfig.getInstance().isDisableGeneratedKeyOnBatch() && result.isForBatch());
		if (gType == GenerationType.IDENTITY) {
			if (supportKeywordDefault) {
				cStr.add(cachedEscapeColumnName);
				vStr.add("DEFAULT");
			}
			if(returnKeys){
				result.setCallback(autoGenerateCall);
			}
		} else {
			String dbKey = result.getTableNames() == null ? null : result.getTableNames().getDatabase();
			OperateTarget db = new OperateTarget(result.parent, dbKey);
			Sequence sh = db.getSequence(this);
			if(!returnKeys && sh.isRawNative()){//可以用简略方式操作
				cStr.add(cachedEscapeColumnName);
				vStr.add(sh.getName()+".nextval");
			}else{
				result.setCallback(new SequenceGenerateCallback(accessor, sh));
				cStr.add(cachedEscapeColumnName);
				vStr.add("?");
				result.addField(field);
			}
		}
	}

	/*
	 * 判断是否已经指定了自增序号的值, 如果用户已经赋了有效的值，那么就无需再自动生成
	 */
	private boolean isAssignedSequence(Object value) {
		if (value instanceof Number) {
			return ((Number) value).longValue() > 0;
		} else {
			return false;
		}
	}

	public Property getAccessor() {
		return accessor;
	}

	public int getSqlType() {
		return isBig ? Types.BIGINT : Types.INTEGER;
	}
}
