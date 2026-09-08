package com.lh.assist.reg.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.sql.SQLException;
import java.util.StringJoiner;
import org.postgresql.util.PGobject;

/**
 * float[] <-> Postgres pgvector 컬럼 변환기
 *
 * String을 반환하면 JDBC가 VARCHAR로 바인딩해 Postgres가 vector 컬럼에 암묵
 * 캐스팅을 거부한다("column is of type vector but expression is of type
 * character varying"). PGobject(type="vector")로 반환해야 pgjdbc 드라이버가
 * 타입을 인식해서 save()로도 정상 insert/update된다
 */
@Converter
public class VectorStringConverter implements AttributeConverter<float[], Object> {

	@Override
	public Object convertToDatabaseColumn(float[] attribute) {
		if (attribute == null) {
			return null;
		}
		StringJoiner joiner = new StringJoiner(",", "[", "]");
		for (float value : attribute) {
			joiner.add(Float.toString(value));
		}
		PGobject pgObject = new PGobject();
		pgObject.setType("vector");
		try {
			pgObject.setValue(joiner.toString());
		} catch (SQLException ex) {
			throw new IllegalStateException("벡터 값을 PGobject로 변환하는 데 실패했습니다", ex);
		}
		return pgObject;
	}

	@Override
	public float[] convertToEntityAttribute(Object dbData) {
		if (dbData == null) {
			return new float[0];
		}
		String raw = dbData instanceof PGobject pgObject ? pgObject.getValue() : dbData.toString();
		if (raw == null || raw.isBlank()) {
			return new float[0];
		}
		String trimmed = raw.trim();
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
