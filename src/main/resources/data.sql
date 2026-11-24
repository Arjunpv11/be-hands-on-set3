insert into booking_guard (id)
select 1 where not exists (select 1 from booking_guard where id = 1);

insert into rooms (name, capacity, active)
select 'Birch', 6, true where not exists (select 1 from rooms where name = 'Birch');
insert into rooms (name, capacity, active)
select 'Cedar', 10, true where not exists (select 1 from rooms where name = 'Cedar');
insert into rooms (name, capacity, active)
select 'Maple', 8, true where not exists (select 1 from rooms where name = 'Maple');
insert into rooms (name, capacity, active)
select 'Oak', 14, true where not exists (select 1 from rooms where name = 'Oak');
insert into rooms (name, capacity, active)
select 'Pine', 4, true where not exists (select 1 from rooms where name = 'Pine');
