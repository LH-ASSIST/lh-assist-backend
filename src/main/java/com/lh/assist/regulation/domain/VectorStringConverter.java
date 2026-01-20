package com.lh.assist.regulation.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.StringJoiner;

@Converter
public class VectorStringConverter implements AttributeConverter<float[], String> {

	@Override
	public String convertToDatabaseColumn(float[] attribute) {
		if (attribute == null) {
			return null;
		}
		StringJoiner joiner = new StringJoiner(",", "[", "]");
		for (float value : attribute) {
			joiner.add(Float.toString(value));
		}
		return joiner.toString();
	}

	@Override
	public float[] convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return null;
		}
		String trimmed = dbData.trim();
		if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
			trimmed = trimmed.substring(1, trimmed.length() - 1);
		}
		if (trimmed.isBlank()) {
			return new float[0];
		}
		String[] parts = trimmed.split(",");
		float[] vector = new float[parts.length];
		for (int i = 0; i < parts.length; i++) {
			vector[i] = Float.parseFloat(parts[i].trim());
		}
		return vector;
	}
}