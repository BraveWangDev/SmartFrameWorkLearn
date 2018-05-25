package org.smart4j.chapter2.helper;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smart4j.chapter2.util.PropsUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
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
                DATA_SOURCE.getConnection();
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
        // 执行数据库操作后,将Connection返还给连接池,不再关闭连接
//        finally {
//            closeConnection();
//        }
        return entityList;
    }
}
