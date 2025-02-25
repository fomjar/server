delete from tbl_instruction where i_inst = (conv('0000200B', 16, 10) + 0);
insert into tbl_instruction values((conv('0000200B', 16, 10) + 0), 'sp', 2, "sp_query_platform_account_money(?, ?, $paid)");

-- 查询游戏
delimiter //
drop procedure if exists sp_query_platform_account_money //
create procedure sp_query_platform_account_money (
    out i_code  integer,
    out c_desc  mediumblob,
    in  paid    integer     -- null：所有账号；非空：指定账号
)
begin
    if paid is null then
        call sp_query_platform_account_money_all(i_code, c_desc);
    else
        call sp_query_platform_account_money_by_paid(i_code, c_desc, paid);
    end if;
end //
delimiter ;
