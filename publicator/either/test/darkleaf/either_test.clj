(ns darkleaf.either-test
  (:require
   [darkleaf.either :as sut]
   [clojure.test :as t]))

(t/deftest step-1
  (t/testing "constructors and deref"
    (t/testing "with value"
      (let [val :val
            l   (sut/left val)
            r   (sut/right val)]
        (t/is (= val @l @r))))
    (t/testing "without value"
      (let [l (sut/left)
            r (sut/right)]
        (t/is (= nil @l @r)))))
  (t/testing "print"
    (let [l (sut/left)
          r (sut/right)]
      (t/is (= "#<Left nil>" (pr-str l)))
      (t/is (= "#<Right nil>" (pr-str r)))))
  (t/testing "predicates"
    (t/testing "left?"
      (t/is (sut/left? (sut/left)))
      (t/is (not (sut/left? (sut/right)))))
    (t/testing "right?"
      (t/is (sut/right? (sut/right)))
      (t/is (not (sut/right? (sut/left)))))
    (t/testing "eihter?"
      (t/is (sut/either? (sut/left)))
      (t/is (sut/either? (sut/right)))
      (t/is (not (sut/either? nil)))))
  (t/testing "invert"
    (let [val :val
          l   (sut/invert (sut/right val))
          r   (sut/invert (sut/left val))]
      (t/is (and (sut/left? l) (= val @l)))
      (t/is (and (sut/right? r) (= val @r)))))
  (t/testing "bimap"
    (let [l (->> 0 sut/left (sut/bimap inc identity))
          r (->> 0 sut/right (sut/bimap identity inc))]
      (t/is (and (sut/left? l) (= 1 @l)))
      (t/is (and (sut/right? r) (= 1 @r)))))
  (t/testing "map-left"
    (let [l (->> 0 sut/left (sut/map-left inc))
          r (->> 0 sut/right (sut/map-left inc))]
      (t/is (and (sut/left? l) (= 1 @l)))
      (t/is (and (sut/right? r) (= 0 @r)))))
  (t/testing "map-right"
    (let [l (->> 0 sut/left (sut/map-right inc))
          r (->> 0 sut/right (sut/map-right inc))]
      (t/is (and (sut/left? l) (= 0 @l)))
      (t/is (and (sut/right? r) (= 1 @r))))))

(t/deftest step-2
  (t/testing "let="
    (t/testing "right"
      (let [ret (sut/let= [x (sut/right 1)
                           y (sut/right 2)]
                  (sut/right (+ x y)))]
        (t/is (sut/right? ret))
        (t/is (= 3 @ret))))
    (t/testing "left"
      (let [ret (sut/let= [x (sut/left 1)
                           y (sut/right 2)]
                  (sut/right (+ x y)))]
        (t/is (sut/left? ret))
        (t/is (= 1 @ret))))
    (t/testing "computation"
      (t/testing "right"
        (let [effect-spy   (promise)
              side-effect! (fn [] (deliver effect-spy :ok))]
          (sut/let= [x (sut/right 1)
                     y (sut/right 2)]
            (side-effect!)
            (sut/right (+ x y)))
          (t/is (realized? effect-spy))))
      (t/testing "left"
        (let [y-spy        (promise)
              effect-spy   (promise)
              side-effect! (fn [] (deliver effect-spy :ok))]
          (sut/let= [x (sut/left 1)
                     y (sut/right (do (deliver y-spy :ok) 2))]
            (side-effect!)
            (sut/right (+ x y)))
          (t/is (not (realized? y-spy)))
          (t/is (not (realized? effect-spy))))))
    (t/testing "destructuring"
      (let [ret (sut/let= [[x y] (sut/right [1 2])]
                  (sut/right (+ x y)))]
        (t/is (= 3 @ret))))
    (t/testing "asserts"
      (t/testing "bindings"
        (t/is (thrown? AssertionError
                       (sut/let= [x 1]
                         (sut/right x)))))
      (t/testing "result"
        (t/is (thrown? AssertionError
                       (sut/let= [x (sut/right 1)]
                         x)))))))

(t/deftest step-3
  (t/testing ">>="
    (t/testing "right rights"
      (let [mv   (sut/right 0)
            inc= (comp sut/right inc)
            str= (comp sut/right str)
            ret  (sut/>>= mv inc= str=)]
        (t/is (sut/right? ret))
        (t/is (= "1" @ret))))
    (t/testing "left right"
      (let [mv   (sut/left 0)
            inc= (comp sut/right inc)
            ret  (sut/>>= mv inc=)]
        (t/is (sut/left? ret))
        (t/is (= 0 @ret))))
    (t/testing "right lefts"
      (let [mv   (sut/right 0)
            fail= (fn [_] (sut/left :error))
            ret  (sut/>>= mv fail=)]
        (t/is (sut/left? ret))
        (t/is (= :error @ret)))))
  (t/testing ">>"
    (t/testing "rights"
      (let [ret (sut/>> (sut/right 1)
                    (sut/right 2))]
        (t/is (sut/right? ret))
        (t/is (= 2 @ret))))
    (t/testing "lefts"
      (let [spy (promise)
            ret (sut/>> (sut/left 1)
                    (sut/right (do (deliver spy :ok) 2)))]
        (t/is (sut/left? ret))
        (t/is (= 1 @ret))
        (t/is (not (realized? spy)))))))
