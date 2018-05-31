package org.smart4j.chapter2.helper;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smart4j.chapter2.util.CollectionUtil;
import org.smart4j.chapter2.util.PropsUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 数据库助手类
 */
public final class DatabaseHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseHelper.class);

    // DbUtils类库提供的QueryRunner对象可以面向实体(Entity)进行查询
    private static final QueryRunner QUERY_RUNNER;

    // 隔离线程:用于保存当前线程的Connection对象
    private static final ThreadLocal<Connection> CONNECTION_HOLDER;

    // DBCP数据库连接池
    private static final BasicDataSource DATA_SOURCE;

    /**
     * 静态初始化
     *      读取数据库配置文件
     */
    static{
        CONNECTION_HOLDER = new ThreadLocal<Connection>();
        QUERY_RUNNER = new QueryRunner();

        Properties conf = PropsUtil.loadProps("config.properties");
        String driver = conf.getProperty("jdbc.driver");
        String url = conf.getProperty("jdbc.url");
        String username = conf.getProperty("jdbc.username");
        String password = conf.getProperty("jdbc.password");

        // 初始化DBCP
        DATA_SOURCE = new BasicDataSource();
        DATA_SOURCE.setDriverClassName(driver);
        DATA_SOURCE.setUrl(url);
        DATA_SOURCE.setUsername(username);
        DATA_SOURCE.setPassword(password);
    }

    /**
     * 获取数据库连接
     */
    public static Connection getConnection() {
        // 每次获取Connection连接,先查看是否存在
        Connection conn = CONNECTION_HOLDER.get();
        if(conn == null){
            try {
                LOGGER.info("创建Connection");
                // 从数据库连接池中获取数据库连接
                conn = DATA_SOURCE.getConnection();
            } catch (SQLException e) {
                LOGGER.error("get connection failure", e);
                throw new RuntimeException(e);
            } finally {
                // 将Connection保存到ThreadLocal中
                CONNECTION_HOLDER.set(conn);
            }
        }
        return conn;
    }

    /**
     * 关闭数据库连接
     */
    public static void closeConnection(){
        // 获取当前线程Connection对象
        Connection conn = CONNECTION_HOLDER.get();
        if(conn != null){
            try {
                conn.close();
                LOGGER.info("销毁Connection");
            } catch (SQLException e) {
                LOGGER.error("close connection failure", e);
                throw new RuntimeException(e);
            } finally {
                // 销毁当前线程的Connection对象
                CONNECTION_HOLDER.remove();
            }
        }
    }

    /**
     * 查询实体列表
     */
    public static <T> List<T> queryEntityList(Class<T> entityClass, String sql, Object... params) {
        List<T> entityList;
        try {
            Connection conn = getConnection();
            entityList = QUERY_RUNNER.query(conn, sql, new BeanListHandler<T>(entityClass), params);
        } catch (SQLException e) {
            LOGGER.error("query entity list failure", e);
            throw new RuntimeException(e);
        }
        return entityList;
    }

    /**
     * 查询实体
     */
    public static <T> T queryEntity(Class<T> entityClass, String sql, Object... params){
        T entity = null;
        try {
            Connection conn = getConnection();
            entity = QUERY_RUNNER.query(conn, sql, new BeanHandler<T>(entityClass), params);
        } catch (SQLException e) {
            LOGGER.error("query entity failure", e);
            throw new RuntimeException(e);
        }
        // 执行数据库操作后,将Connection返还给连接池,不再关闭连接
//        finally {
//            closeConnection();
//        }
        return entity;
    }

    /**
     * 根据sql获取List(对象列名与列值的映射关系)
     */
    public static List<Map<String, Object>> executeQuery(String sql, Object... params){
        // 保存多条数据的对象字段-值映射关系
        List<Map<String, Object>> result = null;
        try {
            Connection conn = getConnection();
            result = QUERY_RUNNER.query(conn, sql, new MapListHandler(), params);
        } catch (SQLException e) {
            LOGGER.error("execute query failure", e);
            throw new RuntimeException(e);
        }
        return result;
    }

    /**
     * 更新
     */
    public static int executeUpdate(String sql, Object... params){
        int rows = 0;// 影响行数
        try {
            Connection conn = getConnection();
            rows = QUERY_RUNNER.update(conn, sql, params);
        } catch (SQLException e) {
            LOGGER.error("execute update failure", e);
            throw new RuntimeException(e);
        }
        return rows;
    }

    /**
     * 获取实体对应的表名
     */
    private static String getTableName(Class<?> entityClass) {
        return entityClass.getSimpleName();
    }

    /**
     * 插入单个实体
     */
    public static <T> boolean insertEntity(Class<T> entityClass, Map<String, Object> fieldMap){

        // check...
        if(CollectionUtil.isEmpty(fieldMap)){
            LOGGER.error("can not insert entity: fieldMap is empty");
            return false;
        }

        String sql = "insert into " + getTableName(entityClass);

        // 声明columns和values两个StringBuilder用于拼装sql
        StringBuilder columns = new StringBuilder("(");// columns : "("fieldName1", "fieldName2", "fieldName3", ...)"
        StringBuilder values = new StringBuilder("(");// values : "("?, ?, ?, ...")"

        // 循环列参数Map,拼装SQL的columns和values部分,放入对应的StringBuilder中备用
        for (String fieldName : fieldMap.keySet()) {
            columns.append(fieldName).append(", ");
            values.append("?, ");
        }

        //将最后一个",",换成结束符")",完成columns和values的拼装
        columns.replace(columns.lastIndexOf(", "), columns.length(), ")");
        values.replace(values.lastIndexOf(", "), values.length(), ")");

        // 拼装完整SQL
        sql += columns + " VALUES " + values;

        // Map转Array
        Object[] params = fieldMap.values().toArray();

        return executeUpdate(sql, params) == 1;
    }

    /**
     * 更新实体
     */
    public static <T> boolean updateEntity(Class<T> entityClass, long id, Map<String, Object> fieldMap){

        // check...
        if(CollectionUtil.isEmpty(fieldMap)){
            LOGGER.error("can not update entity: fieldMap is empty");
            return false;
        }

        String sql = "UPDATE " + getTableName(entityClass) + " SET ";

        // 声明columns的StringBuilder用于拼装sql
        // columns : "fieldName1" = ?, "fieldName2" = ?, "fieldName3" = ?, ...
        StringBuilder columns = new StringBuilder();// 循环列参数Map,拼装SQL的columns部分,放入对应的StringBuilder中备用
        for (String fieldName : fieldMap.keySet()) {
            columns.append(fieldName).append(" = ?, ");
        }

        // 拼装完整SQL
        sql += columns.substring(0, columns.lastIndexOf(", ")) + " WHERE id = ?";

        // 补全参数
        List<Object> paramList = new ArrayList<Object>();
        paramList.addAll(fieldMap.values());
        paramList.add(id);
        // Map转Array
        Object[] params = paramList.toArray();

        return executeUpdate(sql, params) == 1;
    }

    /**
     * 删除实体
     */
    public static <T> boolean deleteEntity(Class<T> entityClass, long id) {
        String sql = "DELETE FROM " + getTableName(entityClass) + " WHERE id = ?";
        return executeUpdate(sql, id) == 1;
    }

    /**
     * 执行SQL文件
     */
    public static void executeSqlFile(String filePath) {
        //获取当前线程上下文中的ClassLoader,通过"sql/customer_init.sql"获取一个InputStream对象
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(filePath);
        //通过输入流创建BufferReader
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        // 循环读取每一行,并调用DatabaseHelper.executeUpdate执行每一条sql

        try {
            String sql;
            while( (sql=reader.readLine()) != null){
                executeUpdate(sql);
            }
        } catch (IOException e) {
            LOGGER.error("execute sql file failure", e);
            throw new RuntimeException(e);
        }
    }

}
