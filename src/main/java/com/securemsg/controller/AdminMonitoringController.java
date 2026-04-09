package com.securemsg.controller;

import com.securemsg.repository.EmailRepository;
import com.securemsg.repository.UserRepository;
import com.securemsg.service.MessageQueueService;
import com.securemsg.service.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Контроллер мониторинга системы для администратора.
 * Отображает статистику писем, информацию о контейнерах,
 * статус очередей ActiveMQ и системную информацию (память, время работы).
 * Доступен только пользователям с ролью ADMIN.
 */
@Controller
@RequestMapping("/admin")
public class AdminMonitoringController {

    private static final Logger log = LoggerFactory.getLogger(AdminMonitoringController.class);

    @Autowired
    private EmailRepository emailRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageQueueService messageQueueService;

    @Autowired
    private SessionManager sessionManager;

    /**
     * Отображает страницу мониторинга системы.
     * Собирает и отображает:
     * <ul>
     *     <li>Статистику писем (всего, непрочитанных, за сегодня)</li>
     *     <li>Информацию о контейнерах приложения</li>
     *     <li>Статус очередей ActiveMQ</li>
     *     <li>Системную информацию (Java версия, время работы, потоки, память)</li>
     * </ul>
     *
     * @param model модель для передачи данных в представление
     * @return название шаблона admin/monitoring
     */
    @GetMapping("/monitoring")
    public String monitoring(Model model) {
        log.info("=== MONITORING PAGE REQUESTED ===");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentUser = auth.getName();
        log.info("Current user: {}", currentUser);

        try {
            // 1. Статистика писем
            long totalEmails = emailRepository.count();
            long unreadEmails = emailRepository.findAll().stream()
                    .filter(e -> !e.isRead())
                    .count();

            LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
            long todayEmails = emailRepository.findAll().stream()
                    .filter(e -> e.getSentDate() != null && e.getSentDate().isAfter(today))
                    .count();

            log.info("Email stats - Total: {}, Unread: {}, Today: {}", totalEmails, unreadEmails, todayEmails);

            // 2. Информация о контейнерах
            Map<String, Integer> containersStats = sessionManager.getContainersStats();
            int totalContainers = containersStats.size();
            int totalUsersOnContainers = containersStats.values().stream().mapToInt(Integer::intValue).sum();

            String instanceId = System.getenv("INSTANCE_ID");
            if (instanceId == null) instanceId = "1";
            String instanceName = System.getenv("INSTANCE_NAME");
            if (instanceName == null) instanceName = "app-" + instanceId;

            log.info("Containers stats - Total containers: {}, Total users: {}", totalContainers, totalUsersOnContainers);
            log.info("Current instance - ID: {}, Name: {}", instanceId, instanceName);

            // 3. Статус очередей
            Map<String, Object> queueInfo = messageQueueService.getQueueInfo();
            int totalPendingMessages = (int) queueInfo.getOrDefault("totalPendingMessages", 0);
            Object queueDetails = queueInfo.getOrDefault("queueDetails", new HashMap<>());

            log.info("Queue stats - Pending messages: {}", totalPendingMessages);

            // 4. Системная информация (безопасно, с защитой от деления на ноль)
            Map<String, Object> systemInfo = getSystemInfoSafe();

            model.addAttribute("currentUser", currentUser);
            model.addAttribute("totalEmails", totalEmails);
            model.addAttribute("unreadEmails", unreadEmails);
            model.addAttribute("todayEmails", todayEmails);
            model.addAttribute("containersStats", containersStats);
            model.addAttribute("totalContainers", totalContainers);
            model.addAttribute("totalUsersOnContainers", totalUsersOnContainers);
            model.addAttribute("instanceId", instanceId);
            model.addAttribute("instanceName", instanceName);
            model.addAttribute("totalPendingMessages", totalPendingMessages);
            model.addAttribute("queueDetails", queueDetails);
            model.addAttribute("systemInfo", systemInfo);

            log.info("Monitoring page loaded successfully");
            return "admin/monitoring";

        } catch (Exception e) {
            log.error("Error in monitoring: {}", e.getMessage(), e);

            // Значения по умолчанию при ошибке
            model.addAttribute("currentUser", currentUser);
            model.addAttribute("totalEmails", 0);
            model.addAttribute("unreadEmails", 0);
            model.addAttribute("todayEmails", 0);
            model.addAttribute("containersStats", new HashMap<>());
            model.addAttribute("totalContainers", 0);
            model.addAttribute("totalUsersOnContainers", 0);
            model.addAttribute("instanceId", "1");
            model.addAttribute("instanceName", "app-1");
            model.addAttribute("totalPendingMessages", 0);
            model.addAttribute("queueDetails", new HashMap<>());

            Map<String, Object> defaultSystemInfo = new HashMap<>();
            defaultSystemInfo.put("javaVersion", System.getProperty("java.version"));
            defaultSystemInfo.put("uptime", "Запущено");
            defaultSystemInfo.put("threadCount", Thread.activeCount());
            defaultSystemInfo.put("heapUsed", 0);
            defaultSystemInfo.put("heapMax", 512);
            model.addAttribute("systemInfo", defaultSystemInfo);

            return "admin/monitoring";
        }
    }

    /**
     * Собирает системную информацию (безопасно, с защитой от ошибок).
     *
     * @return Map с информацией: javaVersion, uptime, threadCount, heapUsed, heapMax
     */
    private Map<String, Object> getSystemInfoSafe() {
        Map<String, Object> info = new HashMap<>();

        info.put("javaVersion", System.getProperty("java.version"));

        try {
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            long uptime = runtimeBean.getUptime();
            info.put("uptime", formatUptime(uptime));
            info.put("threadCount", Thread.activeCount());

            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heapMemory = memoryBean.getHeapMemoryUsage();
            long heapUsed = heapMemory.getUsed() / (1024 * 1024);
            long heapMax = heapMemory.getMax() / (1024 * 1024);

            if (heapMax == 0) heapMax = 512;

            info.put("heapUsed", heapUsed);
            info.put("heapMax", heapMax);

            log.debug("System info - Java: {}, Uptime: {}, Threads: {}, Heap: {}MB/{}MB",
                    info.get("javaVersion"), info.get("uptime"), info.get("threadCount"), heapUsed, heapMax);
        } catch (Exception e) {
            log.warn("Failed to retrieve system info: {}", e.getMessage());

            info.put("uptime", "Запущено");
            info.put("threadCount", Thread.activeCount());
            info.put("heapUsed", 0);
            info.put("heapMax", 512);
        }
        return info;
    }

    /**
     * Форматирует время работы системы в удобочитаемый вид.
     *
     * @param uptimeMillis время работы в миллисекундах
     * @return отформатированная строка (например, "2 д 5 ч" или "3 ч 30 мин" или "45 мин")
     */
    private String formatUptime(long uptimeMillis) {
        long seconds = uptimeMillis / 1000;
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;

        if (days > 0) return String.format("%d д %d ч", days, hours);
        if (hours > 0) return String.format("%d ч %d мин", hours, minutes);
        return String.format("%d мин", minutes);
    }
}