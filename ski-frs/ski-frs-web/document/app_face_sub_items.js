
(function($) {

fomjar.framework.phase.append('dom', frsmain);
fomjar.framework.phase.append('ren', search);

var sid = fomjar.util.args.sid;

function frsmain() {
    build_head();
    build_body();
}

function build_head() {
    frs.ui.head().style_default();
    frs.ui.head().append_item('卡口管理', function() {window.location = 'app_face_outpost.html';});
    frs.ui.head().append_item('特征搜索', function() {window.location = 'app_face_property.html';});
    frs.ui.head().append_item('身份确认', function() {window.location = 'app_face_id.html';});
    frs.ui.head().append_item('实时布控', function() {window.location = 'app_face_monitor.html';});
    frs.ui.head().append_item('轨迹管理', function() {window.location = 'app_face_trail.html';});
    frs.ui.head().append_item('人像库管理', function() {window.location = 'app_face_sub.html';}).addClass('active');
    frs.ui.head().append_item('分析统计');
}

function build_body() {
    frs.ui.layout.lr(frs.ui.body());
    build_body_l();
}

function build_body_l() {
    var choose;
    frs.ui.body().l.append([
        $('<label>姓名</label>'), $("<input placeholder='不限' type='text' >"),
        $('<label>性别</label>'), $("<select><option value='-1'>不限</option><option value='0'>女</option><option value='1'>男</option></select>"),
        $('<label>生日（YYYYMMDD）</label>'), $("<input placeholder='不限' type='text' >"),
        $('<label>身份证号</label>'), $("<input placeholder='不限' type='text' >"),
        $('<label>电话</label>'), $("<input placeholder='不限' type='number' >"),
        $('<label>地址</label>'), $("<input placeholder='不限' type='text' >"),
        new frs.ui.Button('开始搜索', search).to_major()
    ]);
}

function collect() {
    var name    = $(frs.ui.body().l.find('input')[0]).val();
    var gender  = parseInt(frs.ui.body().l.find('select').val());
    var birth   = $(frs.ui.body().l.find('input')[1]).val();
    var idno    = $(frs.ui.body().l.find('input')[2]).val();
    var phone   = $(frs.ui.body().l.find('input')[3]).val();
    var addr    = $(frs.ui.body().l.find('input')[4]).val();
    
    var data = {};
    data.sid    = sid;
    if (name)   data.name   = name;
    if (-1 != gender) data.gender = gender;
    if (birth)  data.birth  = birth;
    if (idno)   data.idno   = idno;
    if (phone)  data.phone  = phone;
    if (addr)   data.addr   = addr;
    return data;
}

var pl = 30;
var pk;

function search() {
    pk = new Date().getTime();
    search_page(1);
}

function search_page(page) {
    var data = collect();
    data.pk = pk;
    data.pf = (page - 1) * pl;
    data.pt = page * pl - 1;
    
    var mask = new frs.ui.Mask();
    var hud = new frs.ui.hud.Major('正在获取');
    mask.appear();
    hud.appear();
    fomjar.net.send(ski.isis.INST_GET_SUB_ITEM, data, function(code, desc) {
        mask.disappear();
        hud.disappear();
        if (code) {
            new frs.ui.hud.Minor(desc).appear(1500);
            return;
        }
        
        var p = desc[0];
        var r = frs.ui.body().r;
        r.children().detach();
        var pager1 = new frs.ui.Pager(page, p.pa, function(i) {search_page(i);});
        var div_pager1 = $('<div></div>');
        div_pager1.append(pager1);
        r.append(div_pager1);
        $.each(desc, function(i, item) {
            if (0 == i) return;
            
            var block = new frs.ui.Block({
                cover   : (item.pics.length > 0 ? item.pics[0].path : ''),
                name    : '所属库名：' + item.sname + '<br/>添加时间：' + new Date(item.time).format('yyyy/MM/dd HH:mm:ss')
            });
            r.append(block);
            block.unbind('click');
            block.bind('click', function() {
                var mask = new frs.ui.Mask();
                var dialog = new frs.ui.Dialog();
                
                mask.bind('click', function() {
                    mask.disappear();
                    dialog.disappear();
                });
                
                dialog.css('width', '60%');
                dialog.append_text_h1c('编辑人像');
                dialog.append_space('.5em');
                dialog.append_input({'placeholder' : '姓名'}).val(item.name);
                var sel_gender;
                dialog.append(sel_gender = $("<select><option value='-1'>未知</option><option value='0'>女</option><option value='1'>男</option></select>"));
                sel_gender.css('width', '100%');
                sel_gender.val(item.gender);
                dialog.append_input({'placeholder' : '生日'}).val(item.birth);
                dialog.append_input({'placeholder' : '身份证号'}).val(item.idno);
                dialog.append_input({'placeholder' : '电话'}).val(item.phone);
                dialog.append_input({'placeholder' : '地址'}).val(item.addr);
                $.each(item.pics, function(i, pic) {
                    var img = $('<img>').attr('src', pic.path);
                    img.css('width', '20%');
                    dialog.append(img);
                });
                var choose = $("<input type='file' accept='image/*'>");
                choose.css('position', 'absolute');
                choose.css('top', '0');
                choose.css('left', '0');
                choose.css('width', '100%');
                choose.css('opacity', '0');
                choose.bind('change', function(e) {
                    var files = e.target.files || e.dataTransfer.files;
                    if (!files || !files[0]) return;
            
                    var file = files[0];
                    fomjar.graphics.image_base64_local(file, function(base64) {
                        var mask1 = new frs.ui.Mask();
                        var hud1 = new frs.ui.hud.Minor('正在上传');
                        mask1.appear();
                        hud1.appear();
                        fomjar.net.send(ski.isis.INST_SET_PIC, {
                            sid     : item.sid,
                            siid    : item.siid,
                            data    : base64,
                            name    : file.name,
                            type    : 0,    // man
                            size    : 2,    // small
                        }, function(code, desc) {
                            mask1.disappear();
                            hud1.disappear();
                            if (code) {
                                new frs.ui.hud.Minor(desc).appear(1500);
                                return;
                            }
                            new frs.ui.hud.Minor('添加成功，请刷新页面查看').appear(1500);
                        });
                    });
                });
                var choose_btn = new frs.ui.Button('添加照片').to_major();
                choose_btn.css('width', '100%');
                dialog.append_buttons([
                    $('<div></div>').append([
                        choose_btn,
                        choose,
                    ]),
                    new frs.ui.Button('更新', function() {
                        var data = {
                            sid     : item.sid,
                            siid    : item.siid
                        };
                        if ($(dialog.find('input')[0]).val()) data.name     = $(dialog.find('input')[0]).val();
                        data.gender = parseInt(dialog.find('select').val());
                        if ($(dialog.find('input')[1]).val()) data.birth    = $(dialog.find('input')[1]).val();
                        if ($(dialog.find('input')[2]).val()) data.idno     = $(dialog.find('input')[2]).val();
                        if ($(dialog.find('input')[3]).val()) data.phone    = $(dialog.find('input')[3]).val();
                        if ($(dialog.find('input')[4]).val()) data.addr     = $(dialog.find('input')[4]).val();
                        var mask1 = new frs.ui.Mask();
                        var hud1 = new frs.ui.hud.Major('正在更新');
                        mask1.appear();
                        hud1.appear();
                        fomjar.net.send(ski.isis.INST_MOD_SUB_ITEM, data, function(code, desc) {
                            mask1.disappear();
                            hud1.disappear();
                            if (code) {
                                new frs.ui.hud.Minor(desc).appear(1500);
                                return;
                            }
                            new frs.ui.hud.Minor('更新成功，请刷新页面查看').appear(1500);
                        });
                    }).to_major()
                ]);
                
                mask.appear();
                dialog.appear();
            });
        });
        var pager2 = new frs.ui.Pager(page, p.pa, function(i) {search_page(i);});
        var div_pager2 = $('<div></div>');
        div_pager2.append(pager2);
        r.append(div_pager2);
    });
}


})(jQuery)

