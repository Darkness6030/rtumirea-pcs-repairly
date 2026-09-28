-- Только демонстрационные данные. Повторный запуск не создаёт дубликаты и не удаляет данные.
BEGIN;
INSERT INTO clients (name, phone, address) VALUES
    ('Анна Демонстрационная', '+70000000001', 'Учебный город, ул. Примерная, 1'),
    ('Иван Тестовый', '+70000000002', 'Учебный город, ул. Примерная, 2'),
    ('Мария Примерова', '+70000000003', 'Учебный город, ул. Примерная, 3')
ON CONFLICT (phone) DO NOTHING;
INSERT INTO technicians (name, phone, specialization) VALUES
    ('Алексей Учебный', '+70000000011', 'Стиральные и посудомоечные машины'),
    ('Ольга Демонстрационная', '+70000000012', 'Холодильники и морозильники'),
    ('Сергей Тестовый', '+70000000013', 'Мелкая бытовая техника')
ON CONFLICT (phone) DO NOTHING;
INSERT INTO repair_requests (client_id, technician_id, appliance_type, brand, model, problem,
                             created_at, completed_at, status, cost)
SELECT c.id, t.id, v.appliance_type, v.brand, v.model, v.problem,
       CURRENT_TIMESTAMP - INTERVAL '3 days',
       CASE WHEN v.status = 'COMPLETED' THEN CURRENT_TIMESTAMP - INTERVAL '1 day' ELSE NULL END,
       v.status, v.cost
FROM (VALUES
    ('+70000000001', NULL, 'Стиральная машина', 'Bosch', 'WAN24260', '[Демо] Не сливает воду', 'NEW', 0.00),
    ('+70000000002', '+70000000012', 'Холодильник', 'Atlant', 'XM 4024', '[Демо] Не охлаждает', 'DIAGNOSING', 1200.00),
    ('+70000000003', '+70000000011', 'Посудомоечная машина', 'Beko', 'DIS 25010', '[Демо] Ошибка подачи воды', 'WAITING_PARTS', 3500.00),
    ('+70000000001', '+70000000013', 'Пылесос', 'Samsung', 'SC4520', '[Демо] Не включается', 'IN_REPAIR', 2100.00),
    ('+70000000002', '+70000000011', 'Стиральная машина', 'LG', 'F2J3NS0W', '[Демо] Замена насоса', 'COMPLETED', 4800.00),
    ('+70000000003', NULL, 'Микроволновая печь', 'Panasonic', 'NN-ST34', '[Демо] Отказ клиента', 'CANCELLED', 0.00)
) AS v(client_phone, technician_phone, appliance_type, brand, model, problem, status, cost)
JOIN clients c ON c.phone = v.client_phone
LEFT JOIN technicians t ON t.phone = v.technician_phone
WHERE NOT EXISTS (SELECT 1 FROM repair_requests r WHERE r.client_id = c.id AND r.problem = v.problem);
COMMIT;
