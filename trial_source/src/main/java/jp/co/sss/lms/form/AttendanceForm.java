package jp.co.sss.lms.form;

import java.util.LinkedHashMap;
import java.util.List;

import lombok.Data;

/**
 * 勤怠フォーム
 * 
 * @author 東京ITスクール
 */
@Data
public class AttendanceForm {

	/** LMSユーザーID */
	private Integer lmsUserId;
	/** グループID */
	private Integer groupId;
	/** 年間計画No */
	private String nenkanKeikakuNo;
	/** ユーザー名 */
	private String userName;
	/** 退校フラグ */
	private Integer leaveFlg;
	/** 退校日 */
	private String leaveDate;
	/** 退校日（表示用） */
	private String dispLeaveDate;
	/** 勤務時間（時間） */
	private LinkedHashMap<Integer, String> hourOptions;
	/** 勤務時間（分） */
	private LinkedHashMap<Integer, String> minuteOptions;
	/** 中抜け時間(プルダウン) */
	private LinkedHashMap<Integer, String> blankTimes;
	/** 日次の勤怠フォームリスト */
	private List<DailyAttendanceForm> attendanceList;

}
