-- Seed learning content. Fixed UUIDs + ON CONFLICT make this idempotent.

INSERT INTO subjects (id, title, description, order_index) VALUES
  ('11111111-1111-1111-1111-111111111111', 'Foundations of Salah',
   'Understand the five daily prayers and build a consistent habit.', 1),
  ('22222222-2222-2222-2222-222222222222', 'Gratitude & Tafakkur',
   'Practices of thankfulness and mindful reflection.', 2),
  ('33333333-3333-3333-3333-333333333333', 'Prophetic Character',
   'Lessons in akhlaq (character) from the Sunnah.', 3)
ON CONFLICT (id) DO NOTHING;

INSERT INTO lessons (id, subject_id, title, body, order_index) VALUES
  ('aaaaaaaa-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
   'Why We Pray', 'Salah is the second pillar of Islam and a direct connection with Allah. This lesson explores its meaning and purpose.', 1),
  ('aaaaaaaa-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111',
   'The Five Daily Prayers', 'Fajr, Dhuhr, Asr, Maghrib and Isha — their times and significance.', 2),
  ('aaaaaaaa-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111',
   'Building Consistency', 'Practical steps and gentle habits to pray on time every day.', 3),
  ('bbbbbbbb-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222',
   'The Practice of Shukr', 'How gratitude reshapes the heart, and simple daily gratitude habits.', 1),
  ('bbbbbbbb-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222',
   'Tafakkur: Mindful Reflection', 'Reflecting on creation and the self as a form of worship.', 2),
  ('cccccccc-0000-0000-0000-000000000001', '33333333-3333-3333-3333-333333333333',
   'Mercy and Patience', 'The Prophet ﷺ as a model of mercy (rahma) and patience (sabr).', 1),
  ('cccccccc-0000-0000-0000-000000000002', '33333333-3333-3333-3333-333333333333',
   'Honesty and Trust', 'Al-Amin: living with truthfulness and trustworthiness.', 2)
ON CONFLICT (id) DO NOTHING;
