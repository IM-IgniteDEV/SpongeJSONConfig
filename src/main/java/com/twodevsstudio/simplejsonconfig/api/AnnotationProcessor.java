package com.twodevsstudio.simplejsonconfig.api;


import com.twodevsstudio.simplejsonconfig.def.Serializer;
import com.twodevsstudio.simplejsonconfig.exceptions.AnnotationProcessException;
import com.twodevsstudio.simplejsonconfig.interfaces.Autowired;
import com.twodevsstudio.simplejsonconfig.interfaces.Configuration;
import com.twodevsstudio.simplejsonconfig.utils.CustomLogger;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.reflections.Reflections;
import org.reflections.scanners.FieldAnnotationsScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.spongepowered.api.plugin.PluginContainer;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;


public class AnnotationProcessor {

  private PluginContainer instance;

  private Reflections reflections;

  public static AnnotationProcessor INSTANCE;

  public static void processAnnotations(@NotNull PluginContainer plugin) {

    if(INSTANCE != null) throw new AnnotationProcessException();

    INSTANCE = new AnnotationProcessor(plugin);

    INSTANCE.processConfiguration();
    INSTANCE.processAutowired();

  }

  private AnnotationProcessor(@NotNull PluginContainer plugin) {

    this.instance = plugin;
    this.reflections = new Reflections(instance.getClass().getPackage().getName()
        , new TypeAnnotationsScanner()
        , new FieldAnnotationsScanner()
        , new SubTypesScanner());
  }

  private void processConfiguration() {

    Set<Class<?>> configurationClasses = reflections.getTypesAnnotatedWith(Configuration.class);

    for(Class<?> annotadedClass : configurationClasses) {

      Configuration configurationAnnotation = annotadedClass.getAnnotation(Configuration.class);
      String configName = configurationAnnotation.name();

      if(!isConfig(annotadedClass)) {
        CustomLogger.warning("Configuration "
            + configName
            + " could not be loaded. Class annotated as @Configuration does not extends "
            + Config.class.getName());

        continue;
      }

      Class<? extends Config> configClass = (Class<? extends Config>) annotadedClass;

      Constructor<? extends Config> constructor;
      Config config;

      try {
        constructor = configClass.getConstructor();
        constructor.setAccessible(true);
        config = constructor.newInstance();
      } catch(ReflectiveOperationException ignored) {
        CustomLogger.warning(configClass.getName() + ": Cannot find default constructor");
        continue;
      }

      String fileName = configName.endsWith(".json") ? configName : configName + ".json";

      Optional<Path> sourcePath = instance.getSource();
      if(!sourcePath.isPresent()) {
        throw new RuntimeException("Cannot find plugin main directory!" + instance.getName());
      }

      initConfig(
          config,
          new File(sourcePath.get().getParent() + "/" + instance.getName() + "/configuration", fileName)
      );

    }

  }

  @SneakyThrows
  private void processAutowired() {

    for(Field field : reflections.getFieldsAnnotatedWith(Autowired.class)) {

      field.setAccessible(true);

      Class<?> type = field.getType();

      if(type.getSuperclass() == Config.class && Modifier.isStatic(field.getModifiers())) {
        field.set(null, Config.getConfig((Class<? extends Config>) type));
      }

    }

  }

  public boolean isConfig(@NotNull Class<?> clazz) {

    return clazz.getSuperclass() == Config.class;
  }

  private void initConfig(@NotNull Config config, @NotNull File configFile) {

    config.configFile = configFile;

    if(!configFile.exists()) {

      try {
        configFile.mkdirs();
        configFile.createNewFile();
        Serializer.getInst().saveConfig(config, configFile);
      } catch(IOException ex) {
        ex.printStackTrace();
        return;
      }

    } else {

      try {
        config.reload();
      } catch(Exception exception) {
        CustomLogger.warning(config.getClass().getName() + ": Config file is corrupted");
        return;
      }

    }

    ConfigContainer.SINGLETONS.put(config.getClass(), config);
  }

}
