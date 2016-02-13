package com.faforever.client.replay;

public class ReplayInfoBeanBuilder {

  private final ReplayInfoBean replayInfoBean;

  private ReplayInfoBeanBuilder(int id) {
    replayInfoBean = new ReplayInfoBean();
    replayInfoBean.setId(id);
  }

  public ReplayInfoBean get() {
    return replayInfoBean;
  }

  public static ReplayInfoBeanBuilder create(int id) {
    return new ReplayInfoBeanBuilder(id);
  }
}
