package com.anusikh.cdcink.repository;

import com.anusikh.cdcink.model.CdcDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface CdcDocumentRepository extends ElasticsearchRepository<CdcDocument, String> {

    List<CdcDocument> findByTable(String table);

    List<CdcDocument> findByDatabase(String database);

    List<CdcDocument> findByOperation(String operation);
}
