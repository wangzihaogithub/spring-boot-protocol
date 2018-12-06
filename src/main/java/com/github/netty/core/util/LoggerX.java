package com.github.netty.core.util;

import com.github.netty.core.constants.CoreConstants;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * JDK日志级别 [SEVERE   WARNING   INFO   CONFIG  FINE   FINER    FINEST]
 *        含意 [严重     警告      信息   配置    良好   较好     最好]
 *
 * @author acer01
 *  2018/8/25/025
 */
public class LoggerX {
    private Logger logger;

    public LoggerX() {
        this.logger = Logger.getGlobal();
    }

    public LoggerX(Class clazz) {
        this.logger = Logger.getLogger(clazz.getName());
    }

    public LoggerX(String name) {
        this.logger = Logger.getLogger(name);
    }

    public LoggerX(Logger logger) {
        this.logger = logger;
    }

    public void debug(String var1,Object...args){
        if(!CoreConstants.isEnableLog()){
            return;
        }
        logger.log(Level.CONFIG, var1,args);
    }

    public void debug(String var1){
        if(!CoreConstants.isEnableLog()){
            return;
        }
        logger.log(Level.CONFIG, var1);
    }
    
    public void debug(String var1,Throwable throwable){
        if(!CoreConstants.isEnableLog()){
            return;
        }
        logger.log(Level.CONFIG, var1,throwable);
    }

    public void info(String var1){
        if(!CoreConstants.isEnableLog()){
            return;
        }
        logger.log(Level.INFO, var1);
    }

    public void info(String var1,Object...args){
        if(!CoreConstants.isEnableLog()){
            return;
        }
        logger.log(Level.INFO, var1,args);
    }

    public void error(String var1){
        if(!CoreConstants.isEnableLog()){
            return;
        }
        logger.log(Level.SEVERE, var1);
    }
    
    public void error(String var1,Throwable throwable){
        if(!CoreConstants.isEnableLog()){
            return;
        }
        logger.log(Level.SEVERE, var1,throwable);
    }
    
    public void warn(String var1,Object...args){
        if(!CoreConstants.isEnableLog()){
            return;
        }
        logger.log(Level.WARNING, var1,args);
    }


    public boolean isInfoEnabled(){
        return logger.isLoggable(Level.INFO);
    }

    public boolean isDebugEnabled(){
        return logger.isLoggable(Level.CONFIG);
    }

}
