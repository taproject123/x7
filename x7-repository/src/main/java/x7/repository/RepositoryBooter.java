/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package x7.repository;


import x7.core.async.CasualWorker;
import x7.core.async.IAsyncTask;
import x7.core.config.Configs;
import x7.repository.BaseRepository.HealthChecker;
import x7.repository.dao.DaoImpl;
import x7.repository.dao.DaoInitializer;
import x7.repository.dao.ShardingDaoImpl;
import x7.repository.mapper.Mapper;
import x7.repository.mapper.MapperFactory;
import x7.repository.mapper.MySqlDialect;
import x7.repository.mapper.OracleDialect;
import x7.repository.pool.DataSourceFactory;
import x7.repository.pool.DataSourcePool;
import x7.repository.redis.JedisConnector_Persistence;
import x7.repository.redis.LevelTwoCacheResolver;

import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;


public class RepositoryBooter {


	private static RepositoryBooter instance = null;
	
	
	
	public static void boot() {
		if (instance == null) {
			instance = new RepositoryBooter();
			init();
			HealthChecker.onStarted();
			CasualWorker.accept(new IAsyncTask() {
				@Override
				public void execute() throws Exception {
					try {
						Thread.sleep(1000);
						generateId();
					}catch (Exception e){
						e.printStackTrace();
					}
				}
			});
		}
	}



	public static void boot(DataSource ds_W, DataSource ds_R) {
		if (instance == null) {
			instance = new RepositoryBooter();
			init(ds_W,ds_R);
			HealthChecker.onStarted();
			CasualWorker.accept(new IAsyncTask() {
				@Override
				public void execute() throws Exception {
					try {
						Thread.sleep(1000);
						generateId();
					}catch (Exception e){
						e.printStackTrace();
					}
				}
			});
		}
	}
	
	public static void generateId(){
		System.out.println("\n" +"----------------------------------------");
		List<IdGenerator> idGeneratorList = SqlRepository.getInstance().list(IdGenerator.class);
		for (IdGenerator generator : idGeneratorList) {
			String name = generator.getClzName();
			long maxId = generator.getMaxId();
			
			String idInRedis = JedisConnector_Persistence.getInstance().hget(BaseRepository.ID_MAP_KEY, name);
			
			System.out.println(name + ",test, idInDB = " + maxId + ", idInRedis = " + idInRedis);
			

			if (idInRedis == null){
				JedisConnector_Persistence.getInstance().hset(BaseRepository.ID_MAP_KEY, name, String.valueOf(maxId));
			}else if (idInRedis != null && maxId > Long.valueOf(idInRedis)){
				JedisConnector_Persistence.getInstance().hset(BaseRepository.ID_MAP_KEY, name, String.valueOf(maxId));
			}
			
			System.out.println(name + ",final, idInRedis = " + JedisConnector_Persistence.getInstance().hget(BaseRepository.ID_MAP_KEY, name));

		
		}
		System.out.println("----------------------------------------"+"\n");
	}
	
	private static void init(){
		onDriver(null);
		init(null,null);
	}


	public static void onDriver(String driverClassName){

		String driver = null;
		if (Objects.isNull(driverClassName)){
			driver = Configs.getString("x7.db.driver");
		}else{
			driver = driverClassName;
		}

		driver = driver.toLowerCase();

		if (driver.contains(DbType.MYSQL)){
			DbType.value = DbType.MYSQL;
			initDialect(new MySqlDialect());
		}else if (driver.contains(DbType.ORACLE)){
			DbType.value = DbType.ORACLE;
			initDialect(new OracleDialect());
		}else if (driver.contains(DbType.DB2)){
			DbType.value = DbType.DB2;
			initDialect(new MySqlDialect());//FIXME
		}else if (driver.contains(DbType.SQLSERVER)){
			DbType.value = DbType.SQLSERVER;
			initDialect(new MySqlDialect());//FIXME
		}
	}
	
	private static void init(DataSource ds_W, DataSource ds_R){
		


		
		switch (DbType.value){
		
		default:
			if (Objects.nonNull(ds_W)){
				DaoInitializer.init(ds_W,ds_R);
			}else {
				DataSourcePool pool = DataSourceFactory.get(null);
				DaoInitializer.init(pool.get(), pool.getR());
				DaoInitializer.init(pool.getDsMapW(), pool.getDsMapR());
			}
			SqlRepository.getInstance().setSyncDao(DaoImpl.getInstance());
			SqlRepository.getInstance().setShardingDao(ShardingDaoImpl.getInstance());
			break;
		
		}

			
		
		if (Configs.isTrue(ConfigKey.IS_CACHEABLE)){
			SqlRepository.getInstance().setCacheResolver(LevelTwoCacheResolver.getInstance());
		}
	}
	
	private static void initDialect(Mapper.Dialect dialect){
		MapperFactory.Dialect = dialect;
		DaoImpl.dialect = dialect;
		ResultSetUtil.dialect = dialect;
	}
	
}
