package com.campus.network.repository;

import com.campus.network.model.NetworkFlow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FlowSearchJdbcRepository {

    public record QueryResult(List<NetworkFlow> flows, long totalElements) {
    }

    private static final RowMapper<NetworkFlow> FLOW_ROW_MAPPER = new RowMapper<>() {
        @Override
        public NetworkFlow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new NetworkFlow(
                    rs.getLong("id"),
                    rs.getString("src_ip"),
                    rs.getString("dst_ip"),
                    rs.getInt("src_port"),
                    rs.getInt("dst_port"),
                    rs.getString("protocol"),
                    rs.getLong("bytes_sent"),
                    rs.getLong("bytes_recv"),
                    rs.getLong("packets_sent"),
                    rs.getLong("packets_recv"),
                    rs.getString("app_protocol"),
                    toLocalDateTime(rs.getTimestamp("start_time")),
                    toLocalDateTime(rs.getTimestamp("end_time")),
                    toLocalDateTime(rs.getTimestamp("timestamp")),
                    rs.getString("region"),
                    rs.getString("direction")
            );
        }
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FlowSearchJdbcRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public QueryResult search(
            String srcIp,
            String dstIp,
            String srcCidr,
            String dstCidr,
            Integer srcPort,
            Integer dstPort,
            Integer dstPortFrom,
            Integer dstPortTo,
            String protocol,
            String appProtocol,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int page,
            int size
    ) {
        StringBuilder where = new StringBuilder(" WHERE timestamp BETWEEN :startTime AND :endTime");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("startTime", Timestamp.valueOf(startTime))
                .addValue("endTime", Timestamp.valueOf(endTime));

        appendTextFilter(where, params, "src_ip", "srcIp", srcIp);
        appendTextFilter(where, params, "dst_ip", "dstIp", dstIp);
        appendCidrFilter(where, params, "src_ip", "srcCidr", srcCidr);
        appendCidrFilter(where, params, "dst_ip", "dstCidr", dstCidr);
        appendEqualsFilter(where, params, "src_port", "srcPort", srcPort);
        appendEqualsFilter(where, params, "dst_port", "dstPort", dstPort);
        appendRangeFilter(where, params, "dst_port", "dstPortFrom", "dstPortTo", dstPortFrom, dstPortTo);
        appendTextFilter(where, params, "protocol", "protocol", protocol);
        appendTextFilter(where, params, "app_protocol", "appProtocol", appProtocol);

        String countSql = "SELECT COUNT(*) FROM network_flows" + where;
        Long total = jdbcTemplate.queryForObject(countSql, params, Long.class);

        String dataSql = """
                SELECT id, src_ip, dst_ip, src_port, dst_port, protocol,
                       bytes_sent, bytes_recv, packets_sent, packets_recv,
                       app_protocol, start_time, end_time, timestamp, region, direction
                FROM network_flows
                """ + where + " ORDER BY timestamp DESC, id DESC LIMIT :limit OFFSET :offset";
        params.addValue("limit", size);
        params.addValue("offset", Math.max(page, 0) * size);

        List<NetworkFlow> flows = jdbcTemplate.query(dataSql, params, FLOW_ROW_MAPPER);
        return new QueryResult(flows, total == null ? 0L : total);
    }

    private void appendTextFilter(StringBuilder where, MapSqlParameterSource params, String column, String paramName, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String normalized = value.trim();
        if (normalized.contains("*")) {
            where.append(" AND ").append(column).append(" ILIKE :").append(paramName);
            params.addValue(paramName, normalized.replace('*', '%'));
            return;
        }
        where.append(" AND LOWER(").append(column).append(") = LOWER(:").append(paramName).append(")");
        params.addValue(paramName, normalized);
    }

    private void appendCidrFilter(StringBuilder where, MapSqlParameterSource params, String column, String paramName, String cidr) {
        if (cidr == null || cidr.isBlank()) {
            return;
        }
        where.append(" AND ").append(column).append("::inet <<= CAST(:").append(paramName).append(" AS cidr)");
        params.addValue(paramName, cidr.trim());
    }

    private void appendEqualsFilter(StringBuilder where, MapSqlParameterSource params, String column, String paramName, Integer value) {
        if (value == null) {
            return;
        }
        where.append(" AND ").append(column).append(" = :").append(paramName);
        params.addValue(paramName, value);
    }

    private void appendRangeFilter(
            StringBuilder where,
            MapSqlParameterSource params,
            String column,
            String fromParam,
            String toParam,
            Integer fromValue,
            Integer toValue
    ) {
        if (fromValue != null) {
            where.append(" AND ").append(column).append(" >= :").append(fromParam);
            params.addValue(fromParam, fromValue);
        }
        if (toValue != null) {
            where.append(" AND ").append(column).append(" <= :").append(toParam);
            params.addValue(toParam, toValue);
        }
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
