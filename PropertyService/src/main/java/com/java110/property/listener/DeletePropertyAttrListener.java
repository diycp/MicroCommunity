package com.java110.property.listener;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.java110.common.constant.BusinessTypeConstant;
import com.java110.common.constant.ResponseConstant;
import com.java110.common.constant.StatusConstant;
import com.java110.common.exception.ListenerExecuteException;
import com.java110.common.util.Assert;
import com.java110.core.annotation.Java110Listener;
import com.java110.core.context.DataFlowContext;
import com.java110.entity.center.Business;
import com.java110.property.dao.IPropertyServiceDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 删除物业信息 侦听
 *
 * 处理节点
 * 1、businessPropertyHouse:{} 物业住户信息节点
 * 2、businessPropertyAttr:[{}] 物业属性信息节点
 * Created by wuxw on 2018/5/18.
 */
@Java110Listener("deletePropertyAttrListener")
@Transactional
public class DeletePropertyAttrListener extends AbstractPropertyBusinessServiceDataFlowListener {

    private final static Logger logger = LoggerFactory.getLogger(DeletePropertyAttrListener.class);
    @Autowired
    IPropertyServiceDao propertyServiceDaoImpl;

    @Override
    public int getOrder() {
        return 3;
    }

    @Override
    public String getBusinessTypeCd() {
        return BusinessTypeConstant.BUSINESS_TYPE_DELETE_PROPERTY_ATTR;
    }

    /**
     * 根据删除信息 查出Instance表中数据 保存至business表 （状态写DEL） 方便撤单时直接更新回去
     * @param dataFlowContext 数据对象
     * @param business 当前业务对象
     */
    @Override
    protected void doSaveBusiness(DataFlowContext dataFlowContext, Business business) {
        JSONObject data = business.getDatas();

        Assert.notEmpty(data,"没有datas 节点，或没有子节点需要处理");

        if(data.containsKey("businessPropertyAttr")){
            JSONArray businessPropertyAttrs = data.getJSONArray("businessPropertyAttr");
            doSaveBusinessPropertyAttrs(business,businessPropertyAttrs);
        }
    }

    /**
     * 删除 instance数据
     * @param dataFlowContext 数据对象
     * @param business 当前业务对象
     */
    @Override
    protected void doBusinessToInstance(DataFlowContext dataFlowContext, Business business) {
        String bId = business.getbId();
        //Assert.hasLength(bId,"请求报文中没有包含 bId");

        //物业信息
        Map info = new HashMap();
        info.put("bId",business.getbId());
        info.put("operate",StatusConstant.OPERATE_DEL);

        //物业属性
        List<Map> businessPropertyAttrs = propertyServiceDaoImpl.getBusinessPropertyAttrs(info);
        if(businessPropertyAttrs != null && businessPropertyAttrs.size() > 0) {
            for(Map businessPropertyAttr : businessPropertyAttrs) {
                flushBusinessPropertyAttr(businessPropertyAttr,StatusConstant.STATUS_CD_INVALID);
                propertyServiceDaoImpl.updatePropertyAttrInstance(businessPropertyAttr);
            }
        }
    }

    /**
     * 撤单
     * 从business表中查询到DEL的数据 将instance中的数据更新回来
     * @param dataFlowContext 数据对象
     * @param business 当前业务对象
     */
    @Override
    protected void doRecover(DataFlowContext dataFlowContext, Business business) {
        String bId = business.getbId();
        //Assert.hasLength(bId,"请求报文中没有包含 bId");
        Map info = new HashMap();
        info.put("bId",bId);
        info.put("statusCd",StatusConstant.STATUS_CD_INVALID);

        Map delInfo = new HashMap();
        delInfo.put("bId",business.getbId());
        delInfo.put("operate",StatusConstant.OPERATE_DEL);

        //物业属性
        List<Map> propertyAttrs = propertyServiceDaoImpl.getPropertyAttrs(info);
        if(propertyAttrs != null && propertyAttrs.size()>0){

            List<Map> businessPropertyAttrs = propertyServiceDaoImpl.getBusinessPropertyAttrs(delInfo);
            //除非程序出错了，这里不会为空
            if(businessPropertyAttrs == null || businessPropertyAttrs.size() ==0 ){
                throw new ListenerExecuteException(ResponseConstant.RESULT_CODE_INNER_ERROR,"撤单失败(property_attr)，程序内部异常,请检查！ "+delInfo);
            }
            for(Map businessPropertyAttr : businessPropertyAttrs) {
                flushBusinessPropertyAttr(businessPropertyAttr,StatusConstant.STATUS_CD_VALID);
                propertyServiceDaoImpl.updatePropertyAttrInstance(businessPropertyAttr);
            }
        }
    }



    /**
     * 保存物业属性信息
     * @param business 当前业务
     * @param businessPropertyAttrs 物业属性
     */
    private void doSaveBusinessPropertyAttrs(Business business,JSONArray businessPropertyAttrs){
        JSONObject data = business.getDatas();

        for(int propertyAttrIndex = 0 ; propertyAttrIndex < businessPropertyAttrs.size();propertyAttrIndex ++){
            JSONObject propertyAttr = businessPropertyAttrs.getJSONObject(propertyAttrIndex);
            Assert.jsonObjectHaveKey(propertyAttr,"attrId","businessPropertyAttr 节点下没有包含 attrId 节点");
            if(propertyAttr.getString("attrId").startsWith("-")){
                throw new ListenerExecuteException(ResponseConstant.RESULT_PARAM_ERROR,"attrId 错误，不能自动生成（必须已经存在的attrId）"+propertyAttr);
            }

            autoSaveDelBusinessPropertyAttr(business,propertyAttr);
        }
    }

    public IPropertyServiceDao getPropertyServiceDaoImpl() {
        return propertyServiceDaoImpl;
    }

    public void setPropertyServiceDaoImpl(IPropertyServiceDao propertyServiceDaoImpl) {
        this.propertyServiceDaoImpl = propertyServiceDaoImpl;
    }
}
