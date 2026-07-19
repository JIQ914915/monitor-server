package com.lzzh.monitor.dao.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PgOperationsMapperScriptTest {
    @Test void mapperXmlParsesAndAppliesAuditBoundary() throws Exception {
        MybatisConfiguration c=parse("mapper/PgOperationsMapper.xml");
        Map<String,Object> p=new HashMap<>();p.put("instanceId",1L);p.put("source","postgresql_log");p.put("category",null);p.put("excludeAudit",true);p.put("sqlState","42P01");p.put("database",null);p.put("user",null);p.put("keyword","relation");p.put("from",Timestamp.from(Instant.now().minusSeconds(60)));p.put("to",Timestamp.from(Instant.now()));p.put("limit",100);p.put("offset",20);
        BoundSql sql=c.getMappedStatement(PgOperationsMapper.class.getName()+".selectEvents").getBoundSql(p);
        assertThat(sql.getSql()).contains("source=?","category<>'audit'").contains("sql_state=?","message ILIKE","LIMIT ? OFFSET ?");
        BoundSql countSql=c.getMappedStatement(PgOperationsMapper.class.getName()+".countEvents").getBoundSql(p);
        assertThat(countSql.getSql()).contains("SELECT count(*)","source=?","category<>'audit'").doesNotContain("LIMIT","OFFSET");
    }
    @Test void eventWriterXmlParses() throws Exception {
        MybatisConfiguration c=parse("mapper/TsPgOperationalEventWriterMapper.xml");
        String namespace=TsPgOperationalEventWriterMapper.class.getName();
        assertThat(c.hasStatement(namespace+".insertStateChanges")).isTrue();
        assertThat(c.hasStatement(namespace+".upsertSnapshots")).isTrue();
    }
    private MybatisConfiguration parse(String path)throws Exception{MybatisConfiguration c=new MybatisConfiguration();try(InputStream in=getClass().getClassLoader().getResourceAsStream(path)){assertThat(in).isNotNull();new XMLMapperBuilder(in,c,path,c.getSqlFragments()).parse();}return c;}
}