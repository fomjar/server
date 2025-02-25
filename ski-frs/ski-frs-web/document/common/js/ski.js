
var ski = {};

(function($) {

ski.isis = {
    INST_AUTHORIZE      : 0x00000010,	// 认证
    
    INST_SET_PIC        : 0x00001000,	// 图片
    INST_DEL_PIC        : 0x00001001,
    INST_MOD_PIC        : 0x00001002,
    INST_GET_PIC        : 0x00001003,
    INST_GET_PIC_FV     : 0x00001004,	// 获取特征向量
    INST_SET_SUB        : 0x00001010,	// 主体库
    INST_DEL_SUB        : 0x00001011,
    INST_MOD_SUB        : 0x00001012,
    INST_GET_SUB        : 0x00001013,
    INST_SET_SUB_ITEM   : 0x00001014,	// 主体
    INST_DEL_SUB_ITEM   : 0x00001015,
    INST_MOD_SUB_ITEM   : 0x00001016,
    INST_GET_SUB_ITEM   : 0x00001017,
    INST_SET_DEV        : 0x00001020,	// 设备
    INST_DEL_DEV        : 0x00001021,
    INST_MOD_DEV        : 0x00001022,
    INST_GET_DEV        : 0x00001023,
    INST_GET_OPP        : 0x00001030,	// 分析识别代理
    INST_SET_MON        : 0x00001040,	// 布控
    INST_DEL_MON        : 0x00001041,
    INST_MOD_MON        : 0x00001042,
    INST_GET_MON        : 0x00001043,
    
    
    INST_APPLY_SUB_IMPORT       : 0x00003000,	// 主体导入
    INST_APPLY_SUB_IMPORT_CHECK : 0x00003001,	// 主体导入前检查
    INST_APPLY_SUB_IMPORT_STATE : 0x00003002,	// 主体导入状态
    INST_APPLY_DEV_IMPORT       : 0x00003010,
    INST_APPLY_DEV_IMPORT_STOP  : 0x00003011,
    INST_APPLY_DEV_IMPORT_STATE : 0x00003012,
};

})(jQuery);