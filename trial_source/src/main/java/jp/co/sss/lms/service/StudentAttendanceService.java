package jp.co.sss.lms.service;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;

import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.dto.LoginUserDto;
import jp.co.sss.lms.entity.TStudentAttendance;
import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.form.AttendanceForm;
import jp.co.sss.lms.form.DailyAttendanceForm;
import jp.co.sss.lms.mapper.TStudentAttendanceMapper;
import jp.co.sss.lms.util.AttendanceUtil;
import jp.co.sss.lms.util.Constants;
import jp.co.sss.lms.util.DateUtil;
import jp.co.sss.lms.util.LoginUserUtil;
import jp.co.sss.lms.util.MessageUtil;
import jp.co.sss.lms.util.TrainingTime;

/**
 * 勤怠情報（受講生入力）サービス
 * 
 * @author 東京ITスクール
 */
@Service
public class StudentAttendanceService {

	@Autowired
	private DateUtil dateUtil;
	@Autowired
	private AttendanceUtil attendanceUtil;
	@Autowired
	private MessageUtil messageUtil;
	@Autowired
	private LoginUserUtil loginUserUtil;
	@Autowired
	private LoginUserDto loginUserDto;
	@Autowired
	private TStudentAttendanceMapper tStudentAttendanceMapper;

	/**
	 * 勤怠一覧情報取得
	 * 
	 * @param courseId
	 * @param lmsUserId
	 * @return 勤怠管理画面用DTOリスト
	 */
	public List<AttendanceManagementDto> getAttendanceManagement(Integer courseId,
			Integer lmsUserId) {

		// 勤怠管理リストの取得
		List<AttendanceManagementDto> attendanceManagementDtoList = tStudentAttendanceMapper
				.getAttendanceManagement(courseId, lmsUserId, Constants.DB_FLG_FALSE);
		for (AttendanceManagementDto dto : attendanceManagementDtoList) {
			// 中抜け時間を設定
			if (dto.getBlankTime() != null) {
				TrainingTime blankTime = attendanceUtil.calcBlankTime(dto.getBlankTime());
				dto.setBlankTimeValue(String.valueOf(blankTime));
			}
			// 遅刻早退区分判定
			AttendanceStatusEnum statusEnum = AttendanceStatusEnum.getEnum(dto.getStatus());
			if (statusEnum != null) {
				dto.setStatusDispName(statusEnum.name);
			}
		}

		return attendanceManagementDtoList;
	}

	// 未入力チェック
	// boolean型に収まる理由は、count > 0という比較演算の結果がtrue/falseになるから
	// containts.javaに定数が定義されている、一元管理することで保守性や可読性の向上につながる
	// 削除フラグを使う場合はContaints.DB_FLG_FALSEのように参照する
	public boolean notEnterAttendance(Integer lmsUserId) {
		Date today = attendanceUtil.getTrainingDate(); // 本日の日付取得
		int count = tStudentAttendanceMapper.notEnterCount(lmsUserId, Constants.DB_FLG_FALSE, today);
		// System.out.println("未入力箇所" + count);   // デバックに使用した。未入力件数を表示させる
		return count > 0; // 未入力があればtrue、なければfalse
	}

	/**
	 * 出退勤更新前のチェック
	 * 
	 * @param attendanceType
	 * @return エラーメッセージ
	 */
	public String punchCheck(Short attendanceType) {
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 権限チェック
		if (!loginUserUtil.isStudent()) {
			return messageUtil.getMessage(Constants.VALID_KEY_AUTHORIZATION);
		}
		// 研修日チェック
		if (!attendanceUtil.isWorkDay(loginUserDto.getCourseId(), trainingDate)) {
			return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_NOTWORKDAY);
		}
		// 登録情報チェック
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		switch (attendanceType) {
		case Constants.CODE_VAL_ATWORK:
			if (tStudentAttendance != null
					&& !tStudentAttendance.getTrainingStartTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			break;
		case Constants.CODE_VAL_LEAVING:
			if (tStudentAttendance == null
					|| tStudentAttendance.getTrainingStartTime().equals("")) {
				// 出勤情報がないため退勤情報を入力出来ません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
			}
			if (!tStudentAttendance.getTrainingEndTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			TrainingTime trainingStartTime = new TrainingTime(
					tStudentAttendance.getTrainingStartTime());
			TrainingTime trainingEndTime = new TrainingTime();
			if (trainingStartTime.compareTo(trainingEndTime) > 0) {
				// 退勤時刻は出勤時刻より後でなければいけません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
			}
			break;
		}
		return null;
	}

	/**
	 * 出勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchIn() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 現在の研修時刻
		TrainingTime trainingStartTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				null);
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		if (tStudentAttendance == null) {
			// 登録処理
			tStudentAttendance = new TStudentAttendance();
			tStudentAttendance.setLmsUserId(loginUserDto.getLmsUserId());
			tStudentAttendance.setTrainingDate(trainingDate);
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setTrainingEndTime("");
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setNote("");
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setFirstCreateDate(date);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendance.setBlankTime(null);
			tStudentAttendanceMapper.insert(tStudentAttendance);
		} else {
			// 更新処理
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendanceMapper.update(tStudentAttendance);
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 退勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchOut() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		// 出退勤時刻
		TrainingTime trainingStartTime = new TrainingTime(
				tStudentAttendance.getTrainingStartTime());
		TrainingTime trainingEndTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				trainingEndTime);
		// 更新処理
		tStudentAttendance.setTrainingEndTime(trainingEndTime.toString());
		tStudentAttendance.setStatus(attendanceStatusEnum.code);
		tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
		tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
		tStudentAttendance.setLastModifiedDate(date);
		tStudentAttendanceMapper.update(tStudentAttendance);
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 勤怠フォームへ設定
	 * 
	 * @param attendanceManagementDtoList
	 * @return 勤怠編集フォーム
	 */
	public AttendanceForm setAttendanceForm(
			List<AttendanceManagementDto> attendanceManagementDtoList) {

		AttendanceForm attendanceForm = new AttendanceForm();
		attendanceForm.setAttendanceList(new ArrayList<DailyAttendanceForm>());
		attendanceForm.setLmsUserId(loginUserDto.getLmsUserId());
		attendanceForm.setUserName(loginUserDto.getUserName());
		attendanceForm.setLeaveFlg(loginUserDto.getLeaveFlg());
		attendanceForm.setHourOptions(attendanceUtil.getHour());
		attendanceForm.setMinuteOptions(attendanceUtil.getMinutes());
		attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());

		// 途中退校している場合のみ設定
		if (loginUserDto.getLeaveDate() != null) {
			attendanceForm
					.setLeaveDate(dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy-MM-dd"));
			attendanceForm.setDispLeaveDate(
					dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy年M月d日"));
		}

		// 勤怠管理リストの件数分、日次の勤怠フォームに移し替え
		// DTOはDBから取得したデータの一時的な入れ物であり、画面表示・入力・送信用のFormに入れ替えている
		for (AttendanceManagementDto attendanceManagementDto : attendanceManagementDtoList) {
			DailyAttendanceForm dailyAttendanceForm = new DailyAttendanceForm();
			dailyAttendanceForm.setStudentAttendanceId(attendanceManagementDto.getStudentAttendanceId());
			dailyAttendanceForm.setTrainingDate(dateUtil.toString(attendanceManagementDto.getTrainingDate()));
			// 出勤時間（"HH:mm"形式）を時間と分に分割してセット
			if (attendanceManagementDto.getTrainingStartTime() != null && !attendanceManagementDto.getTrainingStartTime().isEmpty()) {
				String[] start = attendanceManagementDto.getTrainingStartTime().split(":");
				dailyAttendanceForm.setTrainingStartHour(Integer.parseInt(start[0]));
				dailyAttendanceForm.setTrainingStartMinute(Integer.parseInt(start[1]));
			}
			if (attendanceManagementDto.getTrainingEndTime() != null && !attendanceManagementDto.getTrainingEndTime().isEmpty()) {
				String[] end = attendanceManagementDto.getTrainingEndTime().split(":");
				dailyAttendanceForm.setTrainingEndHour(Integer.parseInt(end[0]));
				dailyAttendanceForm.setTrainingEndMinute(Integer.parseInt(end[1]));
			}			
			if (attendanceManagementDto.getBlankTime() != null) {
				dailyAttendanceForm.setBlankTime(attendanceManagementDto.getBlankTime());
				dailyAttendanceForm.setBlankTimeValue(String.valueOf(
						attendanceUtil.calcBlankTime(attendanceManagementDto.getBlankTime())));
			}
			dailyAttendanceForm.setStatus(String.valueOf(attendanceManagementDto.getStatus()));
			dailyAttendanceForm.setNote(attendanceManagementDto.getNote());
			dailyAttendanceForm.setSectionName(attendanceManagementDto.getSectionName());
			dailyAttendanceForm.setIsToday(attendanceManagementDto.getIsToday());
			dailyAttendanceForm.setDispTrainingDate(dateUtil
					.dateToString(attendanceManagementDto.getTrainingDate(), "yyyy年M月d日(E)"));
			dailyAttendanceForm.setStatusDispName(attendanceManagementDto.getStatusDispName());

			attendanceForm.getAttendanceList().add(dailyAttendanceForm);
		}

		return attendanceForm;
	}

	/**
	 * 勤怠登録・入力チェック
	 * 
	 * @param attendanceForm
	 * @return
	 * @throws ParseException
	 */
	public String check(AttendanceForm attendanceForm) throws ParseException {
		
		// 入力チェック用
		boolean hasError = false;
		boolean[] startHourError = new boolean[attendanceForm.getAttendanceList().size()];
		boolean[] startMinuteError = new boolean[attendanceForm.getAttendanceList().size()];
		boolean[] endHourError = new boolean[attendanceForm.getAttendanceList().size()];
		boolean[] endMinuteError = new boolean[attendanceForm.getAttendanceList().size()];
		boolean[] blankTimeError = new boolean[attendanceForm.getAttendanceList().size()]; // 中抜け時間用も追加
		
		// AttendanceForm.getAttendanceList()をループ
		// 各項目ごとに条件をチェックし、エラーがあればエラーメッセージリストに追加
		// エラーが一つでもあれば、attendance/update画面にエラー付きで返す
		// エラーメッセージの重複を防ぐためListではなくSetを使用
		// ↓対応不要だった
		// 各バリデーションもSet.addに変更し、画面に渡す際にList化して渡すとThymeLeaf側の変更不要で便利
		Set<String> errorList = new LinkedHashSet<>();
		for (int i = 0; i < attendanceForm.getAttendanceList().size(); i++) {
			DailyAttendanceForm daily = attendanceForm.getAttendanceList().get(i);

			// a. 備考文字数
			if (daily.getNote() != null && daily.getNote().length() > 100) {
				errorList.add(messageUtil.getMessage("maxlength", new String[]{"備考", "100"}));
			}

			// b. 出勤時間が一部未入力（時間と分の片方のみ入力）
			boolean startHour = daily.getTrainingStartHour() != null;
			boolean startMinute = daily.getTrainingStartMinute() != null;
			if ((startHour && !startMinute) || (!startHour && startMinute)) {
				if (!startHour) {
					startHourError[i] = true;
				}
				if (!startMinute) {
					startMinuteError[i] = true;
				}
				errorList.add(messageUtil.getMessage("input.invalid", new String[]{"出勤時間"}));
				hasError = true;
			}
			
			// c. 退勤時間が一部未入力（時間と分の片方のみ入力）
			boolean endHour = daily.getTrainingEndHour() != null;
			boolean endMinute = daily.getTrainingEndMinute() != null;
			if ((endHour && !endMinute) || (!endHour && endMinute)) {
				if (!endHour) {
					endHourError[i] = true;
				}
				if (!endMinute) {
					endMinuteError[i] = true;
				}
				errorList.add(messageUtil.getMessage("input.invalid", new String[]{"退勤時間"}));
				hasError = true;
			}
			// d. 退勤時間のみ入力（出勤時間が未入力の状態で退勤時間を入力）
			if (!startHour && !startMinute && (endHour || endMinute)) {
				startHourError[i] = true;
				startMinuteError[i] = true;
				errorList.add(messageUtil.getMessage("attendance.punchInEmpty"));
				hasError = true;
			}
			// e. 出勤時間 > 退勤時間（退勤時間より出勤時間の方が遅い）単位を分にそろえて比較
			if (startHour && startMinute && endHour && endMinute) {
				int start = daily.getTrainingStartHour() * 60 + daily.getTrainingStartMinute();
				int end = daily.getTrainingEndHour() * 60 + daily.getTrainingEndMinute();
				if (start >= end) {
					startHourError[i] = true;
					startMinuteError[i] = true;
					endHourError[i] = true;
					endMinuteError[i] = true;
					errorList.add(messageUtil.getMessage("attendance.trainingTimeRange"));
					hasError = true;
				}
				// f. 中抜け時間が勤務時間を超える（単位をそろえた変数が使えるスコープ内でやると良い）
				if (daily.getBlankTime() != null && (daily.getBlankTime() > (end - start))) {
					blankTimeError[i] = true; // 中抜け時間も赤枠強調表示
					errorList.add(messageUtil.getMessage("attendance.blankTimeError"));
					hasError = true;
				}
			}
		}
		// エラーがある場合は入力画面に戻る
		if (hasError) {
			// 選択肢を再設定
			attendanceForm.setHourOptions(attendanceUtil.getHour());
			attendanceForm.setMinuteOptions(attendanceUtil.getMinutes());
			attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());

			return ?;
		}
	}


	/**
	 * 勤怠登録・更新処理
	 * 
	 * @param attendanceForm
	 * @return 完了メッセージ
	 * @throws ParseException
	 */
	public String update(AttendanceForm attendanceForm) throws ParseException {

		Integer lmsUserId = loginUserUtil.isStudent() ? loginUserDto.getLmsUserId()
				: attendanceForm.getLmsUserId();

		// 現在の勤怠情報（受講生入力）リストを取得
		List<TStudentAttendance> tStudentAttendanceList = tStudentAttendanceMapper
				.findByLmsUserId(lmsUserId, Constants.DB_FLG_FALSE);

		// 入力された情報を更新用のエンティティに移し替え
		Date date = new Date();
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {

			// 更新用エンティティ作成
			TStudentAttendance tStudentAttendance = new TStudentAttendance();
			// 日次勤怠フォームから更新用のエンティティにコピー
			BeanUtils.copyProperties(dailyAttendanceForm, tStudentAttendance);
			// 研修日付
			tStudentAttendance
					.setTrainingDate(dateUtil.parse(dailyAttendanceForm.getTrainingDate()));
			// 現在の勤怠情報リストのうち、研修日が同じものを更新用エンティティで上書き
			for (TStudentAttendance entity : tStudentAttendanceList) {
				if (entity.getTrainingDate().equals(tStudentAttendance.getTrainingDate())) {
					tStudentAttendance = entity;
					break;
				}
			}
			tStudentAttendance.setLmsUserId(lmsUserId);
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			// 出勤時刻整形(未入力時の対応追加)
			String startTime = "";
			if (dailyAttendanceForm.getTrainingStartHour() != null && dailyAttendanceForm.getTrainingStartMinute() != null) {
				startTime = String.format("%02d:%02d",
				dailyAttendanceForm.getTrainingStartHour(),
				dailyAttendanceForm.getTrainingStartMinute());
			}
			tStudentAttendance.setTrainingStartTime(startTime);
			// 退勤時刻整形(未入力時の対応追加)
			String endTime = "";
			if (dailyAttendanceForm.getTrainingEndHour() != null && dailyAttendanceForm.getTrainingEndMinute() != null) {
				endTime = String.format("%02d:%02d",
				dailyAttendanceForm.getTrainingEndHour(),
				dailyAttendanceForm.getTrainingEndMinute());
			}		
			tStudentAttendance.setTrainingEndTime(endTime);
			// 中抜け時間
			tStudentAttendance.setBlankTime(dailyAttendanceForm.getBlankTime());
			// ステータス判定用（出勤時間・退勤時間）
			TrainingTime trainingStartTime = new TrainingTime(startTime);
			TrainingTime trainingEndTime = new TrainingTime(endTime);
			// 遅刻早退ステータス判定
			if ((trainingStartTime != null || trainingEndTime != null)
					&& !dailyAttendanceForm.getStatusDispName().equals("欠席")) {
				AttendanceStatusEnum attendanceStatusEnum = attendanceUtil
						.getStatus(trainingStartTime, trainingEndTime);
				tStudentAttendance.setStatus(attendanceStatusEnum.code);
			}
			// 備考
			tStudentAttendance.setNote(dailyAttendanceForm.getNote());
			// 更新者と更新日時
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			// 削除フラグ
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			// 登録用Listへ追加
			tStudentAttendanceList.add(tStudentAttendance);
		}
		// 登録・更新処理
		for (TStudentAttendance tStudentAttendance : tStudentAttendanceList) {
			if (tStudentAttendance.getStudentAttendanceId() == null) {
				tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
				tStudentAttendance.setFirstCreateDate(date);
				tStudentAttendanceMapper.insert(tStudentAttendance);
			} else {
				tStudentAttendanceMapper.update(tStudentAttendance);
			}
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

}
