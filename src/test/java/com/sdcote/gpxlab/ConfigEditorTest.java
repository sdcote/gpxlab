package com.sdcote.gpxlab;

import coyote.commons.cfg.Config;
import coyote.commons.dataframe.DataFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigEditorTest {
    @Test
    void convertsNestedModelsAndPreservesUnchangedValues() {
        ConfigEditor.ConfigModel root = new ConfigEditor.ConfigModel("Global");
        ConfigEditor.ConfigAttribute number = new ConfigEditor.ConfigAttribute("number", 42);
        root.getAttributes().addAll(number, new ConfigEditor.ConfigAttribute("", ""));
        ConfigEditor.ConfigModel nested = new ConfigEditor.ConfigModel("nested");
        nested.getAttributes().add(new ConfigEditor.ConfigAttribute("name", "value"));
        root.getSections().add(nested);

        Config result = root.toConfig();
        assertEquals(42, result.getObject("number"));
        assertEquals("value", ((DataFrame) result.getObject("nested")).getAsString("name"));
        assertEquals(2, result.getFieldCount());
        number.setValue("43");
        assertEquals("43", root.toConfig().getObject("number"));
        number.setValue("42");
        assertEquals(42, number.getOutputValue());
    }

    @Test
    void convertsArraysAndNestedArraySections() {
        ConfigEditor.ConfigModel root = new ConfigEditor.ConfigModel("Global");
        ConfigEditor.ConfigModel array = new ConfigEditor.ConfigModel("items", true);
        array.getAttributes().addAll(new ConfigEditor.ConfigAttribute("", "first"),
                new ConfigEditor.ConfigAttribute("", "second"), new ConfigEditor.ConfigAttribute("", ""));
        ConfigEditor.ConfigModel child = new ConfigEditor.ConfigModel("");
        child.getAttributes().add(new ConfigEditor.ConfigAttribute("key", "value"));
        array.getSections().add(child);
        root.getSections().add(array);

        DataFrame result = (DataFrame) root.toConfig().getField("items").getObjectValue();
        assertTrue(result.isArray() || result.isArrayBiased());
        assertEquals(3, result.getFieldCount());
        assertEquals("first", result.getField(0).getObjectValue());
        assertEquals("second", result.getField(1).getObjectValue());
        assertEquals("value", ((DataFrame) result.getField(2).getObjectValue()).getAsString("key"));
    }

    @Test
    void recognizesBlankAttributesAndModelLabels() {
        ConfigEditor.ConfigAttribute attribute = new ConfigEditor.ConfigAttribute(null, null);
        assertTrue(attribute.isBlank());
        attribute.setName(" \t ");
        assertTrue(attribute.isBlank());
        attribute.setValue("value");
        assertFalse(attribute.isBlank());
        attribute.setValue(null);
        attribute.setName("name");
        assertFalse(attribute.isBlank());
        ConfigEditor.ConfigModel model = new ConfigEditor.ConfigModel(null);
        assertEquals("{}", model.toString());
        model.setArray(true);
        assertEquals("[]", model.toString());
        model.setName("section");
        assertEquals("section", model.toString());
    }
}