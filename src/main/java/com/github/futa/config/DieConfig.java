package com.github.futa.config;

/**
 * Example configuration POJO.
 * <p>
 * Configurations are saved and loaded to JSON files
 * <p>
 * All fields should be public and mutable.
 * <p>
 * Fields to static inner classes generate nested JSON objects.
 */
public class DieConfig {
    public boolean enabled = false;
    public boolean printToConsole = true;
    public boolean saveToFile = true;
    public String fileName = "death_logs.json";
    public String fileNameTxt = "death_logs.txt";
    public String fileNameChineseTxt = "death_logs_chinese.txt";
    public boolean prettyPrintJson = true;



}
