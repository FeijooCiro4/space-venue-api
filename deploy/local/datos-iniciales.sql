-- Datos mínimos para que la base local funcione. Solo se insertan si faltan.
-- Valores de ejemplo: no son necesariamente los de producción.
INSERT INTO cancellationpolicies (type, days_anticipation, refund_percentage)
SELECT * FROM (SELECT 'FLEXIBLE' AS t, 1 AS d, 100.00 AS r UNION ALL
               SELECT 'MODERATED', 7, 50.00 UNION ALL
               SELECT 'STRICT', 14, 0.00) AS p
WHERE NOT EXISTS (SELECT 1 FROM cancellationpolicies c WHERE c.type = p.t);
