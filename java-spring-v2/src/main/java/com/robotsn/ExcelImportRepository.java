package com.robotsn;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ExcelImportRepository {
  private final JdbcTemplate db;

  public ExcelImportRepository(JdbcTemplate db) {
    this.db = db;
  }

  public String snStatus(String sn) {
    var rows = db.queryForList("select status from sn_registry where sn=?", String.class, sn);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public boolean moduleOccupied(String sn) {
    return db.queryForObject(
        "select exists(select 1 from module_installation where module_sn=? and removed_at is null)",
        Boolean.class,
        sn);
  }

  public void reserveExact(String sn, String actor) {
    String month = sn.substring(4, 8), type = sn.substring(9, 10);
    int seq = Integer.parseInt(sn.substring(11));
    db.update(
        "insert into sn_counter(month,type,last_seq) values(?,?,0) on conflict do nothing",
        month,
        type);
    db.queryForObject(
        "select last_seq from sn_counter where month=? and type=? for update",
        Integer.class,
        month,
        type);
    db.update(
        "insert into sn_registry(sn,status,actor) values (?,'UNUSED',?) on conflict do nothing",
        sn,
        actor);
    db.update(
        "update sn_counter set last_seq=greatest(last_seq,?) where month=? and type=?",
        seq,
        month,
        type);
  }

  public long saveSource(String filename, String hash, String report, String actor) {
    return db.queryForObject(
        "insert into source_import(filename,sha256,report,actor) values (?,?,?::jsonb,?) returning id",
        Long.class,
        filename,
        hash,
        report,
        actor);
  }

  public void saveSheet(long id, String name, String cells) {
    db.update(
        "insert into source_sheet(import_id,name,cells) values (?,?,?::jsonb)", id, name, cells);
  }

  public void linkSource(String sn, long id, int row) {
    db.update(
        "update robot set source=? where sn=?",
        "Excel 导入批次 #" + id + " / 机器人信息管理 / 第 " + row + " 行",
        sn);
  }
}
