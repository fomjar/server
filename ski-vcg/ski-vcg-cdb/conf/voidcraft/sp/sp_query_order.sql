delete from tbl_instruction where i_inst = (conv('00002006', 16, 10) + 0);
insert into tbl_instruction values((conv('00002006', 16, 10) + 0), 'sp', 2, "sp_query_order(?, ?)");

-- 查询订单
delimiter //
drop procedure if exists sp_query_order //
create procedure sp_query_order (
    out i_code  integer,
    out c_desc  mediumblob
)
begin
    call sp_query_order_all(i_code, c_desc);
end //
delimiter ;
