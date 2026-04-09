package com.securemsg.repository;

import com.securemsg.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Репозиторий для работы с сущностью вложения (Attachment).
 * Предоставляет методы для поиска вложений по идентификатору письма.
 */
@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    /**
     * Находит все вложения, принадлежащие указанному письму.
     *
     * @param emailId идентификатор письма
     * @return список вложений письма
     */
    List<Attachment> findByEmailId(Long emailId);
}