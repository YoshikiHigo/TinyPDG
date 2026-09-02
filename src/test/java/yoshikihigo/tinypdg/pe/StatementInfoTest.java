package yoshikihigo.tinypdg.pe;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import yoshikihigo.tinypdg.pe.StatementInfo.CATEGORY;

/**
 * 文の階層が、種別と状態の食い違いを許さないことを確かめる。
 *
 * <p>以前は全ての文が 1 つのクラスで表され、break 文が try 用の catch 節を
 * 抱え、if 文に finally 節を設定でき、どちらも何も起こらなかった。
 */
class StatementInfoTest {

	@Test
	void aClassRejectsACategoryItCannotRepresent() {
		// try は catch 節と finally 節を持つ。単純文では表せない。
		assertThrows(IllegalArgumentException.class,
				() -> new SimpleStatementInfo(null, CATEGORY.Try, 1, 1));
		// 逆も同じ。
		assertThrows(IllegalArgumentException.class,
				() -> new TryStatementInfo(null, CATEGORY.Break, 1, 1));
	}

	@Test
	void theCategoryCanOnlyChangeWithinTheSameShape() {
		// 脱糖した yield を代入文に読み替える場面。どちらも単純文である。
		final StatementInfo statement = new SimpleStatementInfo(null,
				CATEGORY.Yield, 1, 1);
		assertDoesNotThrow(() -> statement.setCategory(CATEGORY.Expression));
		assertEquals(CATEGORY.Expression, statement.getCategory());

		// 別の形へは変えられない。if には条件式と else 節が要る。
		assertThrows(IllegalArgumentException.class,
				() -> statement.setCategory(CATEGORY.If));
	}

	@Test
	void onlyBlocksCanHoldStatements() {
		// BlockInfo を実装するのはブロック以下だけである。visitor はこれを
		// 見て「ここに文を足せるか」を判断している。
		//
		// ここを instanceof で書くとコンパイルが通らない。SimpleStatementInfo
		// は final で BlockInfo を実装しないので、その instanceof は成立の
		// しようがないと javac が判断する。実行時に確かめるまでもない、
		// というのがこの階層で得たものである。テストとしては型の関係を
		// 直接見る。
		assertFalse(BlockInfo.class.isAssignableFrom(SimpleStatementInfo.class),
				"break 文のような単純文は文を抱えられない");
		assertTrue(BlockInfo.class.isAssignableFrom(BlockStatementInfo.class));
		assertTrue(BlockInfo.class.isAssignableFrom(IfStatementInfo.class));
		assertTrue(BlockInfo.class.isAssignableFrom(TryStatementInfo.class));
	}

	@Test
	void collectsVariablesFromTheStateEachShapeActuallyHas() {
		// for の更新式は for にしかない。集計はその状態を持つクラスが行う。
		final ForStatementInfo forStatement = new ForStatementInfo(null,
				CATEGORY.For, 1, 3);
		final ExpressionInfo updater = new ExpressionInfo(
				ExpressionInfo.CATEGORY.SimpleName, 1, 1);
		updater.setText("i");
		forStatement.addUpdater(updater);

		assertTrue(forStatement.getReferencedVariables().contains("i"),
				"更新式の変数が集まること");
	}
}
