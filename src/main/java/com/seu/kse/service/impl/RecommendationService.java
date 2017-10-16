package com.seu.kse.service.impl;

import com.seu.kse.bean.Paper;
import com.seu.kse.bean.User;
import com.seu.kse.bean.UserPaperBehavior;
import com.seu.kse.bean.UserPaperBehaviorKey;
import com.seu.kse.dao.PaperMapper;
import com.seu.kse.dao.UserMapper;
import com.seu.kse.dao.UserPaperBehaviorMapper;
import com.seu.kse.email.EmailSender;
import com.seu.kse.service.recommendation.CB.CBKNNModel;
import com.seu.kse.service.recommendation.CB.PaperDocument;
import com.seu.kse.util.Configuration;
import com.seu.kse.service.recommendation.model.Paper2Vec;
import com.seu.kse.service.recommendation.model.PaperSim;
import com.seu.kse.util.Constant;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by yaosheng on 2017/5/31.
 */


public class RecommendationService {
    private ApplicationContext ac = new ClassPathXmlApplicationContext("classpath:spring-mybatis.xml");
    private PaperDocument paperDocument;
    private static CBKNNModel cmodel;
    private static Paper2Vec paper2Vec;
    private EmailSender emailSender;
    Map<String, List<PaperSim>> res= new HashMap<String, List<PaperSim>>();

    PaperMapper paperDao = (PaperMapper) ac.getBean("paperMapper");
    UserMapper userDao = (UserMapper) ac.getBean("userMapper");
    UserPaperBehaviorMapper userPaperBehaviorDao = (UserPaperBehaviorMapper) ac.getBean("userPaperBehaviorMapper");

    public static CBKNNModel getCBKKModel(){
        if(cmodel != null) return cmodel;
        synchronized (cmodel){
            if(cmodel != null) return  cmodel;
            cmodel = new CBKNNModel();
        }
        return cmodel;
    }

    public static Paper2Vec getPaper2Vec(){
        if(cmodel != null) return paper2Vec;
        synchronized (paper2Vec){
            if(cmodel != null) return  paper2Vec;
            cmodel = new CBKNNModel();
        }
        return paper2Vec;
    }


    public void init(){
        try {
            //paperDocument = new PaperDocument();
            //ClassLoader classloader = Thread.currentThread().getContextClassLoader();
            //String filepath = classloader.getResource(Configuration.sentencesFile).getPath();

            //paperDocument.ToDocument(filepath);
            paper2Vec = new Paper2Vec();
            //训练模型
            //paper2Vec.modelByWord2vce(); //服務器內存溢出，如何解決
            paper2Vec.calPaperVec();
            cmodel = new CBKNNModel(paper2Vec,false);

            emailSender = new EmailSender(Constant.sender,Constant.emailhost);
            emailSender.init();
            System.out.println("init complete!");
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        }
    }



    public void updateModel(){
        //判断什么时候需要更新模型
        //若需要更新模型，重新计算论文向量
        //生产文件


        paperDocument.ToDocument(RecommendationService.class.getClassLoader().getResource(Configuration.sentencesFile).getPath());
        //1 . 重新训练词向量

        paper2Vec.modelByWord2vce();
        //2 . 计算paper向量
        paper2Vec.calPaperVec();
        //3. 计算用户近似的向量

        res = cmodel.model();
    }

    public void recommend(int k){
        res = cmodel.model();
        Byte yes =1;
        Byte no = 0;
        for(Map.Entry<String,List<PaperSim>> e : res.entrySet()){
            String email = e.getKey();
            User user = userDao.selectByEmail(email);
            List<PaperSim> val = e.getValue();
            List<String> paperURLs = new ArrayList<String>();
            List<String> paperTitls = new ArrayList<String>();
            for(int i=0;i<k;i++){
                String paperID = val.get(i).getPid();
                Paper paper = paperDao.selectByPrimaryKey(paperID);
                String paperTitle = paper.getTitle();
                String paperURL = Constant.paperinfoURL + paperID;
                paperURLs.add(paperURL);
                paperTitls.add(paperTitle);
                //更新user_paper 表
                UserPaperBehavior upb = new UserPaperBehavior();
                upb.setUid(user.getId());
                upb.setPid(paperID);
                upb.setReaded(yes);
                upb.setInterest(0);
                upb.setAuthor(no);
                updateUserPaperB(upb);
            }
            recommendByEmail(email, paperURLs,paperTitls);
        }
    }

    /**
     * 更新userPaper表
     * @param newRecord
     */
    public void updateUserPaperB(UserPaperBehavior newRecord){
        UserPaperBehaviorKey key = new UserPaperBehaviorKey(newRecord.getUid(),newRecord.getPid());
        UserPaperBehavior old = userPaperBehaviorDao.selectByPrimaryKey(key);
        if(old == null){
            userPaperBehaviorDao.insert(newRecord);
        }else{
            userPaperBehaviorDao.updateByPrimaryKey(newRecord);
        }
    }

    public void recommendByEmail(String email, List<String> paperURIs, List<String> paperTitls){
        String content = "下面是今日为您推荐的论文:"+"<br>";

        for(int i=0;i<paperURIs.size();i++){
            content = content + "<a href=\""+paperURIs.get(i)+"\">"+(i+1) +" : "+ paperTitls.get(i)+"</a>"+"<br>";
        }
        emailSender.send(email,content);
    }

    @Test
    public void run(){
        RecommendationService rs =new RecommendationService();
        rs.updateModel();
        rs.recommend(5);
    }
}
