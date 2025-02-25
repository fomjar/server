
(function($) {

fomjar.framework.phase.append('dom', frsmain);

var urll = fomjar.util.args.urll;
var urlr = fomjar.util.args.urlr;

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
    frs.ui.head().append_item('轨迹管理', function() {window.location = 'app_face_trail.html';}).addClass('active');
    frs.ui.head().append_item('人像库管理', function() {window.location = 'app_face_sub.html';});
    frs.ui.head().append_item('分析统计');
}

function build_body() {
    frs.ui.layout.lr(frs.ui.body());
    build_body_l();
}

var dids;

function build_body_l() {
    var image = $('<img>');
    var input = $("<input type='file' accept='image/*'>");
    var input_tv = $("<input type='number' placeholder='默认70'>");
    var choose;
    
    frs.ui.body().l.append([
        $('<label>上传</label>'),
        $('<div></div>').append([image, input]),
        $('<label>相似度(1~99)</label>'), input_tv,
        $('<label>设备</label>'), choose = new frs.ui.Button('选择设备', function() {
            dids = [];
            frs.ui.choose_devs(function(devs) {
                $.each(devs, function(i, dev) {dids.push(dev.did);});
                choose.text('已选择：' + devs.length + ' 项');
            });
        }).to_major(),
        new frs.ui.Button('开始搜索', func_upload_init).to_major()
    ]);

    input.bind('change', function(e) {
        var files = e.target.files || e.dataTransfer.files;
        if (!files || !files[0]) return;

        var file = files[0];
        fomjar.graphics.image_base64_local(file, function(base64) {image.attr('src', base64);});
    });
    input_tv[0].max = 99;
    input_tv[0].min = 1;
    
    if (urll) input.val(urll);
    if (urlr) {
        fomjar.graphics.image_base64_remote(urlr, function(base64) {image.attr('src', base64);});
    }
}

var pl = 30;	// page length
var pk;
var fv;

function func_upload_init() {
    if (!frs.ui.body().l.find('img').attr('src')) {
        new frs.ui.hud.Minor('必须要选择一张图片').appear(1500);
        return;
    }
    if (!dids) {
        new frs.ui.hud.Minor('一定要选择设备').appear(1500);
        return;
    }
    var mask = new frs.ui.Mask();
    var hud = new frs.ui.hud.Major('正在上传');
    mask.appear();
    hud.appear();
    fomjar.net.send(ski.isis.INST_GET_PIC_FV, {
        data : frs.ui.body().l.find('img').attr('src'),
    }, function(code, desc) {
        mask.disappear();
        hud.disappear();
        if (code) {
            new frs.ui.hud.Minor('识别异常！code=' + code).appear(1500);
            return;
        }
        fv = desc.fv;
        pk = new Date().getTime();
        func_upload_pages(1);
    });
}

var sel_pics = {};

function func_upload_pages(page) {
    var min = 0.7;
    var input = frs.ui.body().l.find('input[type=number]');
    if (input.val()) {
        min = parseFloat(input.val()) / 100;
    }
    var pf = (page - 1) * pl;
    var pt = page * pl - 1;
    
    var mask = new frs.ui.Mask();
    var hud = new frs.ui.hud.Major('正在匹配');
    mask.appear();
    hud.appear();
    fomjar.net.send(ski.isis.INST_GET_PIC, {
        fv      : fv,	// feature vector
        min     : min,	// min fv
        max     : 1.0,	// max fv
        dids    : dids,	// in which devices
        pk      : pk,	// page key
        pf      : pf,	// page from
        pt      : pt,	// page to
    }, function(code, desc) {
        mask.disappear();
        hud.disappear();
        if (code) {
            new frs.ui.hud.Minor(desc).appear(1500);
            return;
        }
        var p = desc[0];
        var r = frs.ui.body().r;
        r.children().detach();
        
        var show_trail = new frs.ui.Button('显示轨迹', function() {
            var mask = new frs.ui.Mask();
            var dialog = new frs.ui.Dialog();
            mask.bind('click', function() {
                mask.disappear();
                dialog.disappear();
            });
            var list = new frs.ui.List();
            dialog.css('width', '50%');
            dialog.append_text_h1c('轨迹');
            dialog.append_space('.5em');
            dialog.append(list);
            
            var pics = new Array();
            
            for (var i in sel_pics) pics.push(sel_pics[i]);
            pics = pics.sort(function(p1, p2) {return p1.time - p2.time;});
            $.each(pics, function(i, pic) {
                list.append_cell({
                    major   : (i + 1) + '. ' + pic.dpath,
                    minor   : new Date(pic.time).format('yyyy/MM/dd HH:mm:ss'),
                });
            });
            
            mask.appear();
            dialog.appear();
        }).to_major();
        show_trail.css('position', 'absolute');
        show_trail.css('top', '0');
        show_trail.css('right', '0');
        show_trail.css('z-index', '10');
        r.append(show_trail);
        
        var pager1 = new frs.ui.Pager(page, p.pa, function(i) {func_upload_pages(i);});
        var div_pager1 = $('<div></div>');
        div_pager1.append(pager1);
        r.append(div_pager1);
        $.each(desc, function(i, pic) {
            if (0 == i) return;
            
            var block;
            r.append(block = new frs.ui.Block({
                cover   : pic.path,
                name    : '相似度：' + (100 * pic.tv).toFixed(1) + '%'
                        + '<br/>时间：' + new Date(pic.time).format('yyyy/MM/dd HH:mm:ss')
            }));
            
            if (sel_pics[pic.pid]) {
                block.is_select = true;
                block.addClass('block-active');
            } else block.is_select = false;
            
            block.unbind('click');
            block.bind('click', function() {
                block.is_select = !block.is_select;
                if (block.is_select) {
                    block.addClass('block-active');
                    sel_pics[pic.pid] = pic;
                } else {
                    block.removeClass('block-active');
                    delete sel_pics[pic.pid];
                }
            });
        });
        var pager2 = new frs.ui.Pager(page, p.pa, function(i) {func_upload_pages(i);});
        var div_pager2 = $('<div></div>');
        div_pager2.append(pager2);
        r.append(div_pager2);
    });
}

})(jQuery)

