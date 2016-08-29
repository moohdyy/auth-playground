-- :name insert-user :! :n
insert into user (username, fullname, email)
values (:username, :fullname, :email)

-- :name delete-user :! :n
delete from user
where username = :username

-- :name rename-user :! :n
update user
set username = :new-username
where username = :username

-- :name update-user :! :n
update user
set fullname = :fullname, email = :email
where username = :username

-- :name update-encrypted-password :! :n
update user
set password = :password, reset = false
where username = :username

-- :name set-user-reset :! :n
update user
set reset = :reset?
where username = :username

-- :name select-user-reset :? :1
select reset from user
where username = :username

-- :name select-user :? :1
select username, fullname, email from user
where username = :username

-- :name select-user-with-password :? :1
select username, fullname, email, password from user
where username = :username
order by username

-- :name select-users :? :n
select username, fullname, email from user
order by username

-- :name user-id :? :1
select id from user
where username = :username

-- :name tenant-id :? :1
select id from tenant
where name = :name

-- :name insert-tenant-user :! :n
insert into tenant_user (tenant_id, user_id)
values (:tenant-id, :user-id)

-- :name delete-tenant-user :! :n
delete from tenant_user
where tenant_id = :tenant-id
and user_id = :user-id

-- :name select-tenants-by-user :? :n
select t.name, t.config from tenant t
inner join tenant_user tu on t.id = tu.tenant_id
inner join user u on tu.user_id = u.id
where u.username = :username
order by name

-- :name tenant-user-id :? :1
select tu.id from tenant_user tu
inner join tenant t on tu.tenant_id = t.id
inner join user u on tu.user_id = u.id
where u.username = :username
and t.name = :tenant-name

-- :name role-id :? :1
select r.id from role r
inner join tenant t on r.tenant_id = t.id
where r.name = :role-name
and t.name = :tenant-name

-- :name insert-tenant-user-role :! :n
insert into tenant_user_role (tenant_user_id, role_id)
values (:tenant-user-id, :role-id)

-- :name delete-tenant-user-role :! :n
delete from tenant_user_role
where tenant_user_id = :tenant-user-id
and role_id = :role-id