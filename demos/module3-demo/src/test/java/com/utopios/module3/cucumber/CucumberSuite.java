package com.utopios.module3.cucumber;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Point d'entrée Cucumber pour JUnit Platform.
 *
 * Cette classe configure :
 * - les fichiers .feature à exécuter (features/)
 * - le package des Step Definitions (glue)
 * - le format de rapport (pretty = lisible en console)
 *
 * Aucune logique ici — juste de la configuration.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.utopios.module3.cucumber")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
public class CucumberSuite {
    // Classe vide — la magie est dans les annotations
}
