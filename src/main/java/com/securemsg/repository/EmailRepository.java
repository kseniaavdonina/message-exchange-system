package com.securemsg.repository;

import com.securemsg.entity.Email;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Репозиторий для работы с сущностью письма (Email).
 * Предоставляет методы для поиска писем по получателю, отправителю,
 * а также поиска по содержимому в папках INBOX и SENT.
 */
@Repository
public interface EmailRepository extends JpaRepository<Email, Long> {

    /**
     * Находит все письма получателя в указанной папке, отсортированные по дате отправки (от новых к старым).
     *
     * @param recipientEmail email получателя
     * @param folder         папка ("INBOX" или "SENT")
     * @return список писем
     */
    List<Email> findByRecipientEmailAndFolderOrderBySentDateDesc(String recipientEmail, String folder);

    /**
     * Находит все письма отправителя в указанной папке, отсортированные по дате отправки (от новых к старым).
     *
     * @param senderEmail email отправителя
     * @param folder      папка ("INBOX" или "SENT")
     * @return список писем
     */
    List<Email> findBySenderEmailAndFolderOrderBySentDateDesc(String senderEmail, String folder);

    /**
     * Выполняет поиск по входящим письмам пользователя (папка INBOX).
     * Поиск осуществляется по теме письма (subject) и тексту (content) без учёта регистра.
     *
     * @param username email пользователя
     * @param query    поисковый запрос
     * @return список писем, соответствующих запросу, отсортированных по дате отправки
     */
    @Query("SELECT e FROM Email e WHERE e.recipientEmail = :username AND e.folder = 'INBOX' " +
            "AND (LOWER(e.subject) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(e.content) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(e.senderEmail) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "ORDER BY e.sentDate DESC")
    List<Email> searchInInbox(@Param("username") String username, @Param("query") String query);

    /**
     * Выполняет поиск по отправленным письмам пользователя (папка SENT).
     * Поиск осуществляется по теме письма (subject) и тексту (content) без учёта регистра.
     *
     * @param username email пользователя
     * @param query    поисковый запрос
     * @return список писем, соответствующих запросу, отсортированных по дате отправки
     */
    @Query("SELECT e FROM Email e WHERE e.senderEmail = :username AND e.folder = 'SENT' " +
            "AND (LOWER(e.subject) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(e.content) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(e.recipientEmail) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "ORDER BY e.sentDate DESC")
    List<Email> searchInSent(@Param("username") String username, @Param("query") String query);

    /**
     * Возвращает общее количество писем в системе.
     *
     * @return количество писем
     */
    long count();
}